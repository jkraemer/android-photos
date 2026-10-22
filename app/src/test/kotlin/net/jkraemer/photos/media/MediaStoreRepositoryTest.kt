package net.jkraemer.photos.media

import android.database.MatrixCursor
import android.provider.MediaStore.Files.FileColumns
import java.time.Instant
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MediaStoreRepositoryTest {
    private fun itemsOf(vararg rows: Array<Any?>) = MatrixCursor(MediaStoreRepository.PROJECTION)
        .apply { rows.forEach { addRow(it) } }
        .use { MediaStoreRepository.itemsFrom(it) }

    @Test fun `maps image and video rows`() {
        val items = itemsOf(
            arrayOf(7L, FileColumns.MEDIA_TYPE_IMAGE, "image/jpeg", 1_792_000_000_000L, 1L, null),
            arrayOf(8L, FileColumns.MEDIA_TYPE_VIDEO, "video/mp4", null, 1_792_000_100L, 4200L),
        )
        assertEquals(
            listOf(
                MediaItem(7, Instant.ofEpochMilli(1_792_000_000_000), MediaKind.IMAGE, "image/jpeg", 0),
                MediaItem(8, Instant.ofEpochSecond(1_792_000_100), MediaKind.VIDEO, "video/mp4", 4200),
            ),
            items,
        )
    }

    @Test fun `rows come back in timeline order`() {
        val items = itemsOf(
            arrayOf(2L, FileColumns.MEDIA_TYPE_IMAGE, "image/jpeg", 2_000L, 1L, null),
            arrayOf(1L, FileColumns.MEDIA_TYPE_IMAGE, "image/jpeg", 1_000L, 1L, null),
        )
        assertEquals(listOf(1L, 2L), items.map { it.id })
    }

    @Test fun `selection pins the camera folder and drops DNG`() {
        assertEquals(listOf("DCIM/Camera/", "image/x-adobe-dng"), MediaStoreRepository.SELECTION_ARGS.toList())
        assertTrue(MediaStoreRepository.SELECTION.contains("relative_path = ?"))
    }

    @Test fun `content uri uses the image or video collection`() {
        val t = Instant.EPOCH
        assertEquals(
            "content://media/external/images/media/7",
            contentUriOf(MediaItem(7, t, MediaKind.IMAGE, "image/jpeg", 0)).toString(),
        )
        assertEquals(
            "content://media/external/video/media/7",
            contentUriOf(MediaItem(7, t, MediaKind.VIDEO, "video/mp4", 0)).toString(),
        )
    }

    @Test fun `first timeline arrives without waiting out the change debounce`() {
        val repository = MediaStoreRepository(RuntimeEnvironment.getApplication().contentResolver)
        val elapsed = measureTimeMillis { runBlocking { repository.timeline().first() } }
        assertTrue("took $elapsed ms", elapsed < 200)
    }
}
