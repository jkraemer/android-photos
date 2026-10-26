package net.jkraemer.photos.media

import android.database.MatrixCursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.time.Instant
import kotlinx.coroutines.runBlocking
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.MediaKind
import net.jkraemer.photos.core.MediaMetadata
import net.jkraemer.photos.media.MediaStoreMetadataSource.Companion.withExif
import net.jkraemer.photos.media.MediaStoreMetadataSource.Companion.withVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.util.DataSource

@RunWith(RobolectricTestRunner::class)
class MetadataSourceTest {
    private val context = RuntimeEnvironment.getApplication()
    private fun fixture() = javaClass.getResourceAsStream("/exif-pixel.jpg")!!
    private fun exif() = ExifInterface(fixture())
    private val base = MediaMetadata("PXL_1.jpg", "image/jpeg")
    private val photo = MediaItem(7, Instant.EPOCH, MediaKind.IMAGE, "image/jpeg", 0)

    @Test fun `reads camera and exposure from EXIF`() {
        val m = base.withExif(exif(), withLocation = true)
        assertEquals("Google", m.make)
        assertEquals("Pixel 10 Pro", m.model)
        assertEquals("Pixel 10 Pro back camera 6.9mm f/1.68", m.lensModel)
        assertEquals(24, m.focalLength35mm)
        assertEquals(6.9, m.focalLengthMm!!, 1e-6)
        assertEquals(1.68, m.fNumber!!, 1e-6)
        assertEquals(82.0 / 15625, m.exposureTimeSeconds!!, 1e-9)
        assertEquals(51, m.iso)
        assertEquals(0.0, m.exposureBiasEv!!, 1e-9)
        assertEquals(54.5021, m.location!!.latitude, 1e-4)
        assertEquals(11.2312, m.location!!.longitude, 1e-4)
    }

    @Test fun `reading without location leaves it out`() =
        assertNull(base.withExif(exif(), withLocation = false).location)

    @Test fun `a file without EXIF keeps every field empty`() {
        val bare = ExifInterface(javaClass.getResourceAsStream("/exif-pixel.jpg")!!.readBytes().let(::stripped))
        assertEquals(base, base.withExif(bare, withLocation = true))
    }

    private fun baseOf(vararg row: Any?) = MatrixCursor(MediaStoreMetadataSource.PROJECTION)
        .apply { addRow(row) }
        .use { it.moveToFirst(); MediaStoreMetadataSource.baseFrom(it, "image/jpeg") }

    @Test fun `maps MediaStore columns`() = assertEquals(
        MediaMetadata("PXL_1.jpg", "image/jpeg", sizeBytes = 2_959_304, width = 4080, height = 3072),
        baseOf("PXL_1.jpg", 2_959_304L, 4080, 3072, "DCIM/Camera/"),
    )

    @Test fun `null columns stay null`() =
        assertEquals(MediaMetadata("PXL_1.jpg", "image/jpeg"), baseOf("PXL_1.jpg", null, null, null, "DCIM/Camera/"))

    private fun videoRetriever(): MediaMetadataRetriever {
        val path = "/test/PXL_1.mp4"
        ShadowMediaMetadataRetriever.addMetadata(path, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, "1920")
        ShadowMediaMetadataRetriever.addMetadata(path, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, "1080")
        ShadowMediaMetadataRetriever.addMetadata(path, MediaMetadataRetriever.METADATA_KEY_DURATION, "26000")
        ShadowMediaMetadataRetriever.addMetadata(path, MediaMetadataRetriever.METADATA_KEY_LOCATION, "+54.5021+011.2312/")
        return MediaMetadataRetriever().apply { setDataSource(path) }
    }

    @Test fun `reads video size, duration and location`() {
        val m = MediaMetadata("PXL_1.mp4", "video/mp4").withVideo(videoRetriever(), withLocation = true)
        assertEquals(1920, m.width)
        assertEquals(1080, m.height)
        assertEquals(26_000L, m.durationMs)
        assertEquals(54.5021, m.location!!.latitude, 1e-4)
        assertEquals(11.2312, m.location!!.longitude, 1e-4)
    }

    @Test fun `video without location permission leaves it out`() =
        assertNull(MediaMetadata("PXL_1.mp4", "video/mp4").withVideo(videoRetriever(), withLocation = false).location)

    /** Opens the fixture and records how; refuses the original like a revoked permission does. */
    private class RecordingOpener(private val refuseOriginal: Boolean, private val open: () -> java.io.InputStream) : ExifOpener {
        val requests = mutableListOf<Boolean>()
        override fun open(uri: Uri, requireOriginal: Boolean): java.io.InputStream {
            requests += requireOriginal
            if (requireOriginal && refuseOriginal) throw SecurityException("ACCESS_MEDIA_LOCATION revoked")
            return open()
        }
    }

    @Test fun `reading without location never asks for the original`() {
        val opener = RecordingOpener(refuseOriginal = false, ::fixture)
        val m = runBlocking { MediaStoreMetadataSource(context.contentResolver, context, opener).read(photo, withLocation = false) }
        assertEquals(listOf(false), opener.requests)
        assertNull(m.location)
    }

    @Test fun `reading with location asks for the original`() {
        val opener = RecordingOpener(refuseOriginal = false, ::fixture)
        val m = runBlocking { MediaStoreMetadataSource(context.contentResolver, context, opener).read(photo, withLocation = true) }
        assertEquals(listOf(true), opener.requests)
        assertEquals(54.5021, m.location!!.latitude, 1e-4)
    }

    @Test fun `a revoked permission reads as no location`() {
        val opener = RecordingOpener(refuseOriginal = true, ::fixture)
        val m = runBlocking { MediaStoreMetadataSource(context.contentResolver, context, opener).read(photo, withLocation = true) }
        assertEquals(listOf(true, false), opener.requests)
        assertEquals("Google", m.make)
        assertNull(m.location)
    }

    @Test fun `a file gone before it is read keeps what MediaStore knows`() {
        val gone = ExifOpener { _, _ -> throw java.io.FileNotFoundException("deleted by another app") }
        val m = runBlocking { MediaStoreMetadataSource(context.contentResolver, context, gone).read(photo, withLocation = true) }
        assertEquals("image/jpeg", m.mimeType)
        assertNull(m.make)
    }

    @Test fun `a file the EXIF parser chokes on keeps what MediaStore knows`() {
        val broken = ExifOpener { _, _ ->
            object : java.io.InputStream() {
                override fun read(): Int = throw IllegalStateException("malformed segment")
            }
        }
        val m = runBlocking { MediaStoreMetadataSource(context.contentResolver, context, broken).read(photo, withLocation = false) }
        assertEquals("image/jpeg", m.mimeType)
        assertNull(m.make)
    }

    @Test fun `a row without a name reads as an empty name`() =
        assertEquals(MediaMetadata("", "image/jpeg"), baseOf(null, null, null, null, "DCIM/Camera/"))

    @Test fun `a video that cannot be opened keeps what MediaStore knows`() {
        val video = MediaItem(8, Instant.EPOCH, MediaKind.VIDEO, "video/mp4", 0)
        // What the real retriever throws for a file that is gone.
        ShadowMediaMetadataRetriever.addException(
            DataSource.toDataSource(context, contentUriOf(video)),
            IllegalArgumentException("setDataSource failed"),
        )
        val m = runBlocking { MediaStoreMetadataSource(context.contentResolver, context).read(video, withLocation = false) }
        assertEquals("video/mp4", m.mimeType)
        assertNull(m.durationMs)
    }
}

/** The JPEG without its APP1 (EXIF) segment. */
private fun stripped(jpeg: ByteArray): java.io.InputStream {
    // SOI, then APP1: FF E1, two length bytes (which count themselves).
    check(jpeg[2] == 0xFF.toByte() && jpeg[3] == 0xE1.toByte())
    val length = ((jpeg[4].toInt() and 0xFF) shl 8) or (jpeg[5].toInt() and 0xFF)
    return (jpeg.copyOfRange(0, 2) + jpeg.copyOfRange(4 + length, jpeg.size)).inputStream()
}
