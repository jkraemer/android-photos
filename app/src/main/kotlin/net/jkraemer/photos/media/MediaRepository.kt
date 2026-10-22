package net.jkraemer.photos.media

import android.content.ContentResolver
import android.database.ContentObserver
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.MediaKind
import net.jkraemer.photos.core.captureInstant
import net.jkraemer.photos.core.inTimelineOrder

/** The camera roll as a live timeline. */
interface MediaRepository {
    /** Emits the full timeline once on collection and again after every change. */
    fun timeline(): Flow<List<MediaItem>>
}

class MediaStoreRepository(private val resolver: ContentResolver) : MediaRepository {
    private val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    @OptIn(FlowPreview::class)
    override fun timeline(): Flow<List<MediaItem>> = changes()
        .conflate()
        // Saving one photo fires several notifications in a burst.
        .debounce(300)
        .map { query() }
        .flowOn(Dispatchers.IO)

    private fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(collection, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    // Without QUERY_ARG_MATCH_PENDING/TRASHED, MediaStore leaves pending and
    // trashed rows out by itself.
    private fun query(): List<MediaItem> =
        resolver.query(collection, PROJECTION, SELECTION, SELECTION_ARGS, null)
            ?.use { itemsFrom(it) }
            .orEmpty()

    companion object {
        val PROJECTION = arrayOf(
            FileColumns._ID,
            FileColumns.MEDIA_TYPE,
            FileColumns.MIME_TYPE,
            FileColumns.DATE_TAKEN,
            FileColumns.DATE_MODIFIED,
            FileColumns.DURATION,
        )

        // Only the camera's own folder: Google Camera keeps RAW files in
        // DCIM/Camera/Raw/, which the exact path match excludes; the MIME check
        // keeps a DNG saved next to its JPEG from showing up twice.
        val SELECTION = "${FileColumns.RELATIVE_PATH} = ? AND " +
            "${FileColumns.MEDIA_TYPE} IN (${FileColumns.MEDIA_TYPE_IMAGE}, ${FileColumns.MEDIA_TYPE_VIDEO}) AND " +
            "${FileColumns.MIME_TYPE} != ?"
        val SELECTION_ARGS = arrayOf("DCIM/Camera/", "image/x-adobe-dng")

        fun itemsFrom(cursor: Cursor): List<MediaItem> {
            val id = cursor.getColumnIndexOrThrow(FileColumns._ID)
            val type = cursor.getColumnIndexOrThrow(FileColumns.MEDIA_TYPE)
            val mime = cursor.getColumnIndexOrThrow(FileColumns.MIME_TYPE)
            val taken = cursor.getColumnIndexOrThrow(FileColumns.DATE_TAKEN)
            val modified = cursor.getColumnIndexOrThrow(FileColumns.DATE_MODIFIED)
            val duration = cursor.getColumnIndexOrThrow(FileColumns.DURATION)
            val items = ArrayList<MediaItem>(cursor.count)
            while (cursor.moveToNext()) {
                items.add(
                    MediaItem(
                        id = cursor.getLong(id),
                        takenAt = captureInstant(
                            if (cursor.isNull(taken)) null else cursor.getLong(taken),
                            cursor.getLong(modified),
                        ),
                        kind = if (cursor.getInt(type) == FileColumns.MEDIA_TYPE_VIDEO) MediaKind.VIDEO else MediaKind.IMAGE,
                        mimeType = cursor.getString(mime),
                        durationMs = if (cursor.isNull(duration)) 0 else cursor.getLong(duration),
                    ),
                )
            }
            return items.inTimelineOrder()
        }
    }
}
