package net.jkraemer.photos.media

import android.content.Intent
import android.net.Uri
import java.time.Instant
import net.jkraemer.photos.core.GeoPoint
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaActionsTest {
    @Test fun `share intent carries the item uri and mime type`() {
        val chooser = shareIntent(MediaItem(7, Instant.EPOCH, MediaKind.IMAGE, "image/jpeg", 0))
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("image/jpeg", send.type)
        assertEquals(
            Uri.parse("content://media/external/images/media/7"),
            send.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java),
        )
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test fun `map intent views the point as a geo link`() {
        val view = mapIntent(GeoPoint(54.5021, 11.2312))
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals(Uri.parse("geo:54.5021,11.2312?q=54.5021,11.2312"), view.data)
    }
}
