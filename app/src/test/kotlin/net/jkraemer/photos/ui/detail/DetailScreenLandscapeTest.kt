package net.jkraemer.photos.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import net.jkraemer.photos.FakeInfoSource
import net.jkraemer.photos.core.DateLabels
import net.jkraemer.photos.core.GeoPoint
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.item
import net.jkraemer.photos.useFakeImages
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w915dp-h412dp-land")
class DetailScreenLandscapeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val labels = DateLabels(Locale.UK, ZoneId.of("Europe/Berlin"))
    private val start = Instant.parse("2026-10-22T08:00:00Z")
    private val items = (1L..20L).map { item(it, start.plusSeconds(3600 * it)) }
    private val noActions = object : DetailActions {
        override fun share(item: MediaItem) {}
        override fun trash(item: MediaItem) {}
        override fun showOnMap(point: GeoPoint) {}
        override fun panelOpened() {}
        override fun showLocation() {}
    }

    @Before fun images() = useFakeImages()

    @Test fun `in landscape the panel sits right of the photo`() {
        compose.setContent {
            DetailScreen(items, 10, labels, onClose = {}, actions = noActions, info = FakeInfoSource(), locationGranted = true)
        }
        compose.onNodeWithTag("pager").performTouchInput { swipeUp() }
        compose.waitForIdle()
        val pager = compose.onNodeWithTag("pager").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("info-panel").fetchSemanticsNode().boundsInRoot
        assertTrue("pager $pager, panel $panel", pager.right <= panel.left + 1f)
        assertTrue("panel $panel", panel.width > 0f)
    }
}
