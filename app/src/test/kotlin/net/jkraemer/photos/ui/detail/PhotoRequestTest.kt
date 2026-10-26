package net.jkraemer.photos.ui.detail

import androidx.compose.ui.unit.Density
import coil3.memory.MemoryCache
import coil3.request.Options
import java.time.Instant
import net.jkraemer.photos.item
import net.jkraemer.photos.media.MediaThumbnailKeyer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PhotoRequestTest {
    @Test fun `a photo shows its film strip thumbnail while the full image loads`() {
        val context = RuntimeEnvironment.getApplication()
        val density = Density(3f)
        val photo = item(7, Instant.parse("2026-10-22T08:00:00Z"))
        val stripKey = MediaThumbnailKeyer().key(stripThumbnail(photo, density), Options(context))
        assertEquals(MemoryCache.Key(stripKey), photoRequest(context, photo, density).placeholderMemoryCacheKey)
    }
}
