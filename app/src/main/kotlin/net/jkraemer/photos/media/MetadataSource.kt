package net.jkraemer.photos.media

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.MediaKind
import net.jkraemer.photos.core.MediaMetadata
import net.jkraemer.photos.core.geoPointOrNull
import net.jkraemer.photos.core.parseIso6709
import net.jkraemer.photos.core.rawOriginalName

/** Opens a photo for its EXIF; [requireOriginal] asks for the unredacted file, with its location. */
internal fun interface ExifOpener {
    fun open(uri: Uri, requireOriginal: Boolean): InputStream
}

/**
 * Reads what the info panel shows from MediaStore, the photo's EXIF and the
 * video's container. EXIF goes through androidx's ExifInterface: the
 * platform's has no LensModel tag. Android strips locations from both unless the app holds
 * ACCESS_MEDIA_LOCATION and asks for the original file.
 */
class MediaStoreMetadataSource internal constructor(
    private val resolver: ContentResolver,
    private val context: Context,
    private val exifOpener: ExifOpener,
) {
    constructor(resolver: ContentResolver, context: Context) : this(
        resolver,
        context,
        ExifOpener { uri, requireOriginal ->
            checkNotNull(resolver.openInputStream(if (requireOriginal) MediaStore.setRequireOriginal(uri) else uri)) {
                "No stream for $uri"
            }
        },
    )

    suspend fun read(item: MediaItem, withLocation: Boolean): MediaMetadata = withContext(Dispatchers.IO) {
        val uri = contentUriOf(item)
        var relativePath: String? = null
        val base = resolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            relativePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaColumns.RELATIVE_PATH))
            baseFrom(cursor, item.mimeType)
        } ?: MediaMetadata(displayName = "", mimeType = item.mimeType)
        val withRaw = base.copy(hasRawOriginal = hasRawOriginal(base.displayName, relativePath))
        // Another app can delete the file between the query and the read (Immich
        // freeing space); the panel then shows what MediaStore knew.
        when (item.kind) {
            MediaKind.IMAGE -> try {
                orWithoutOriginal(withLocation) { original ->
                    exifOpener.open(uri, original).use { withRaw.withExif(ExifInterface(it), original) }
                }
            } catch (_: IOException) {
                withRaw
            }
            // The retriever reports a file it cannot open as IllegalArgumentException
            // or a bare RuntimeException.
            MediaKind.VIDEO -> try {
                orWithoutOriginal(withLocation) { original ->
                    MediaMetadataRetriever().use { retriever ->
                        retriever.setDataSource(context, if (original) MediaStore.setRequireOriginal(uri) else uri)
                        withRaw.withVideo(retriever, original)
                    }
                }
            } catch (_: RuntimeException) {
                withRaw
            }
        }
    }

    /**
     * Reads the original when [withLocation] is asked for. The permission can be
     * revoked between the check and the read; Android then refuses the original,
     * which means no location rather than no metadata.
     */
    private fun <T> orWithoutOriginal(withLocation: Boolean, read: (original: Boolean) -> T): T =
        if (!withLocation) read(false)
        else try {
            read(true)
        } catch (_: SecurityException) {
            read(false)
        }

    private fun hasRawOriginal(displayName: String, relativePath: String?): Boolean {
        val dngName = rawOriginalName(displayName) ?: return false
        if (relativePath == null) return false
        return resolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            arrayOf(MediaColumns._ID),
            "${MediaColumns.DISPLAY_NAME} = ? AND ${MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(dngName, relativePath),
            null,
        )?.use { it.count > 0 } ?: false
    }

    companion object {
        val PROJECTION = arrayOf(
            MediaColumns.DISPLAY_NAME,
            MediaColumns.SIZE,
            MediaColumns.WIDTH,
            MediaColumns.HEIGHT,
            MediaColumns.RELATIVE_PATH,
        )

        /** The row [cursor] points at, as far as MediaStore knows it. */
        fun baseFrom(cursor: Cursor, mimeType: String): MediaMetadata = MediaMetadata(
            displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaColumns.DISPLAY_NAME)),
            mimeType = mimeType,
            sizeBytes = cursor.longOrNull(MediaColumns.SIZE),
            width = cursor.longOrNull(MediaColumns.WIDTH)?.toInt(),
            height = cursor.longOrNull(MediaColumns.HEIGHT)?.toInt(),
        )

        fun MediaMetadata.withExif(exif: ExifInterface, withLocation: Boolean): MediaMetadata = copy(
            make = exif.getAttribute(ExifInterface.TAG_MAKE),
            model = exif.getAttribute(ExifInterface.TAG_MODEL),
            lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL),
            focalLengthMm = exif.doubleOrNull(ExifInterface.TAG_FOCAL_LENGTH),
            focalLength35mm = exif.intOrNull(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM),
            fNumber = exif.doubleOrNull(ExifInterface.TAG_F_NUMBER),
            exposureTimeSeconds = exif.doubleOrNull(ExifInterface.TAG_EXPOSURE_TIME),
            iso = exif.intOrNull(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
            exposureBiasEv = exif.doubleOrNull(ExifInterface.TAG_EXPOSURE_BIAS_VALUE),
            location = if (withLocation) exif.latLong?.let { geoPointOrNull(it[0], it[1]) } else null,
        )

        /** Resolution, duration and location from the container; MediaStore's values where it has none. */
        fun MediaMetadata.withVideo(retriever: MediaMetadataRetriever, withLocation: Boolean): MediaMetadata = copy(
            width = retriever.intOrNull(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: width,
            height = retriever.intOrNull(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: height,
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
            location = if (withLocation) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let(::parseIso6709)
            } else {
                null
            },
        )

        private fun Cursor.longOrNull(column: String): Long? =
            getColumnIndexOrThrow(column).let { if (isNull(it)) null else getLong(it) }

        // A missing tag is null, never 0: a photo without an ISO has no ISO, not ISO 0.
        private fun ExifInterface.doubleOrNull(tag: String): Double? =
            if (hasAttribute(tag)) getAttributeDouble(tag, Double.NaN).takeUnless { it.isNaN() } else null

        private fun ExifInterface.intOrNull(tag: String): Int? =
            if (hasAttribute(tag)) getAttributeInt(tag, -1).takeUnless { it == -1 } else null

        private fun MediaMetadataRetriever.intOrNull(key: Int): Int? = extractMetadata(key)?.toIntOrNull()
    }
}
