package net.jkraemer.photos.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import net.jkraemer.photos.FakeInfoSource
import net.jkraemer.photos.core.DateLabels
import net.jkraemer.photos.core.GeoPoint
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.MediaKind
import net.jkraemer.photos.item
import net.jkraemer.photos.pixelMetadata
import net.jkraemer.photos.useFakeImages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class DetailScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val labels = DateLabels(Locale.UK, ZoneId.of("Europe/Berlin"))
    private val start = Instant.parse("2026-10-22T08:00:00Z")
    /** Ids 1 (oldest) to 20, one hour apart. */
    private val hourly = (1L..20L).map { item(it, start.plusSeconds(3600 * it)) }
    private val closed = mutableListOf<Long?>()
    private val noActions = object : DetailActions {
        override fun share(item: MediaItem) {}
        override fun trash(item: MediaItem) {}
        override fun showOnMap(point: GeoPoint) {}
        override fun panelOpened() {}
        override fun showLocation() {}
    }

    @Before fun images() = useFakeImages()

    private var items by mutableStateOf(hourly)

    /** Records which item each button passed; the screen's wiring is what is under test. */
    private val shared = mutableListOf<Long>()
    private val trashed = mutableListOf<Long>()
    private val shownOnMap = mutableListOf<GeoPoint>()
    private var panelOpenings = 0
    private var showLocationTaps = 0
    private val recordingActions = object : DetailActions {
        override fun share(item: MediaItem) { shared += item.id }
        override fun trash(item: MediaItem) { trashed += item.id }
        override fun showOnMap(point: GeoPoint) { shownOnMap += point }
        override fun panelOpened() { panelOpenings++ }
        override fun showLocation() { showLocationTaps++ }
    }

    private fun open(
        id: Long,
        actions: DetailActions = noActions,
        info: InfoSource = FakeInfoSource(),
        locationGranted: Boolean = true,
    ) {
        compose.setContent {
            DetailScreen(
                items,
                id,
                labels,
                onClose = { closed += it },
                actions = actions,
                info = info,
                locationGranted = locationGranted,
            )
        }
        compose.waitForIdle()
    }

    private fun showsItem(id: Long) =
        compose.onNodeWithTag("timestamp").assertTextEquals(labels.timestamp(hourly.first { it.id == id }.takenAt))

    @Test fun `opens on the tapped item`() {
        open(10)
        showsItem(10)
    }

    @Test fun `tap hides and restores the UI`() {
        open(10)
        compose.onNodeWithTag("pager").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("top-bar").assertDoesNotExist()
        compose.onNodeWithTag("pager").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("top-bar").assertExists()
    }

    @Test fun `double tap zooms and hides the UI`() {
        open(10)
        compose.onNodeWithTag("pager").performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("top-bar").assertDoesNotExist()
    }

    @Test fun `dragging right shows the older item`() {
        open(10)
        compose.onNodeWithTag("pager").performTouchInput { swipeRight() }
        compose.waitForIdle()
        showsItem(9)
    }

    @Test fun `dragging left shows the newer item`() {
        open(10)
        compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        showsItem(11)
    }

    @Test fun `swipe down closes on the current item`() {
        open(10)
        compose.onNodeWithTag("pager").performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertEquals(listOf<Long?>(10), closed)
    }

    @Test fun `back arrow closes`() {
        open(10)
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf<Long?>(10), closed)
    }

    @Test fun `system back closes`() {
        open(10)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(listOf<Long?>(10), closed)
    }

    @Test fun `current item removed from outside lands on the older neighbour`() {
        open(10)
        items = hourly.filter { it.id != 10L }
        compose.waitForIdle()
        showsItem(9)
    }

    @Test fun `all items removed closes`() {
        open(10)
        items = emptyList()
        compose.waitForIdle()
        assertEquals(listOf<Long?>(null), closed)
    }

    @Test fun `newer item arriving keeps the current item`() {
        open(10)
        items = hourly + item(21, start.plusSeconds(3600 * 21))
        compose.waitForIdle()
        showsItem(10)
    }

    @Test fun `older item arriving keeps the current item`() {
        open(10)
        items = listOf(item(0, start)) + hourly
        compose.waitForIdle()
        showsItem(10)
    }

    // Thumbnails are 36dp wide with 2dp gaps; at density 1 that is 38px a slot.
    private val slotPitch = 38f

    /** How far [id]'s thumbnail sits right of the strip's centre, in px. */
    private fun offCentre(id: Long): Float {
        val strip = compose.onNodeWithTag("film-strip").fetchSemanticsNode().boundsInRoot
        return compose.onNodeWithTag("strip-$id").fetchSemanticsNode().boundsInRoot.center.x - strip.center.x
    }

    @Test fun `strip centres and marks the current item`() {
        open(10)
        assertEquals(0f, offCentre(10), 1f)
        compose.onNodeWithTag("strip-current").onChild().assert(hasTestTag("strip-10"))
    }

    @Test fun `strip centres the oldest item too`() {
        open(1)
        assertEquals(0f, offCentre(1), 1f)
    }

    @Test fun `strip moves with the finger while the photo is dragged`() {
        open(10)
        // Half a page towards the newer item moves the strip half a slot.
        compose.onNodeWithTag("pager").performTouchInput {
            down(center)
            moveBy(Offset(-width / 4f, 0f))
            moveBy(Offset(-width / 4f, 0f))
        }
        compose.waitForIdle()
        assertEquals(-slotPitch / 2, offCentre(10), 4f)
        compose.onNodeWithTag("pager").performTouchInput { up() }
    }

    @Test fun `tapping a strip thumbnail shows it at once and centres it`() {
        open(10)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("strip-13").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        showsItem(13)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(0f, offCentre(13), 1f)
    }

    @Test fun `dragging the strip left scrubs newer`() {
        open(10)
        compose.onNodeWithTag("film-strip").performTouchInput {
            swipe(start = center, end = center - Offset(3.3f * slotPitch, 0f), durationMillis = 1_000)
        }
        compose.waitForIdle()
        showsItem(13)
        assertEquals(0f, offCentre(13), 1f)
    }

    @Test fun `flinging the strip keeps it rolling after release`() {
        open(10)
        compose.onNodeWithTag("film-strip").performTouchInput {
            swipe(start = center, end = center - Offset(2f * slotPitch, 0f), durationMillis = 40)
        }
        compose.waitForIdle()
        // The finger let go two slots on; momentum carries the strip further.
        val shown = compose.onNodeWithTag("timestamp").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text
        val shownId = hourly.first { labels.timestamp(it.takenAt) == shown }.id
        assertTrue("landed on $shownId", shownId > 13)
        assertEquals(0f, offCentre(shownId), 1f)
    }

    @Test fun `strip hides with the UI`() {
        open(10)
        compose.onNodeWithTag("pager").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("strip-current").assertDoesNotExist()
    }

    @Test fun `share button shares the current item`() {
        open(10, recordingActions)
        compose.onNodeWithContentDescription("Share").performClick()
        assertEquals(listOf(10L), shared)
    }

    @Test fun `delete button trashes the current item`() {
        open(10, recordingActions)
        compose.onNodeWithTag("pager").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Delete").performClick()
        assertEquals(listOf(9L), trashed)
    }

    @Test fun `video items show a play button`() {
        items = hourly + item(21, start.plusSeconds(3600 * 21), MediaKind.VIDEO, durationMs = 5_000)
        open(21)
        compose.onNodeWithTag("play").assertIsDisplayed()
    }

    @Test fun `removing the current item never blanks or shows the wrong item`() {
        open(10)
        compose.mainClock.autoAdvance = false
        items = hourly.filter { it.id != 10L }
        // The first frame may still show item 10 (it has not seen the new
        // list yet); no frame may lose the pager or name another item.
        val allowed = listOf(9L, 10L).map { id -> labels.timestamp(hourly.first { it.id == id }.takenAt) }
        repeat(5) {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("pager").assertExists()
            compose.onNodeWithTag("timestamp").assert(hasText(allowed[0]) or hasText(allowed[1]))
        }
        showsItem(9)
    }

    private fun openPanel() {
        compose.onNodeWithTag("pager").performTouchInput { swipeUp() }
        compose.waitForIdle()
    }

    private fun showsFile(id: Long) = compose.onNodeWithTag("info-file").assertTextEquals("item-$id.jpg")

    @Test fun `swiping up opens the panel in place of the film strip`() {
        open(10, recordingActions)
        openPanel()
        compose.onNodeWithTag("info-panel").assertIsDisplayed()
        showsFile(10)
        compose.onNodeWithTag("film-strip").assertDoesNotExist()
        assertEquals(1, panelOpenings)
    }

    @Test fun `a short swipe up snaps back`() {
        open(10, recordingActions)
        compose.onNodeWithTag("pager").performTouchInput {
            swipe(start = center, end = center - Offset(0f, 50f), durationMillis = 1_000)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("info-panel").assertDoesNotExist()
        compose.onNodeWithTag("film-strip").assertExists()
        assertEquals(0, panelOpenings)
    }

    @Test fun `the photo sits above the panel`() {
        open(10)
        openPanel()
        val pager = compose.onNodeWithTag("pager").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("info-panel").fetchSemanticsNode().boundsInRoot
        assertTrue("pager $pager, panel $panel", pager.bottom <= panel.top + 1f)
        assertTrue("panel $panel", panel.height > 0f)
    }

    @Test fun `swiping down on the photo closes the panel, not the view`() {
        open(10)
        openPanel()
        compose.onNodeWithTag("pager").performTouchInput { swipeDown() }
        compose.waitForIdle()
        compose.onNodeWithTag("info-panel").assertDoesNotExist()
        compose.onNodeWithTag("film-strip").assertExists()
        assertEquals(emptyList<Long?>(), closed)
    }

    @Test fun `back closes the panel first, then the view`() {
        open(10)
        openPanel()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("info-panel").assertDoesNotExist()
        assertEquals(emptyList<Long?>(), closed)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(listOf<Long?>(10), closed)
    }

    @Test fun `paging sideways keeps the panel open with the new item's file`() {
        open(10)
        openPanel()
        compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("info-panel").assertIsDisplayed()
        showsFile(11)
    }

    @Test fun `a slow read for the previous item never shows on the current one`() {
        open(10, info = FakeInfoSource(delayFor = { if (it.id == 10L) 5_000 else 0 }))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("pager").performTouchInput { swipeUp() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        repeat(100) {
            compose.mainClock.advanceTimeBy(100)
            val files = compose.onAllNodesWithTag("info-file").fetchSemanticsNodes()
                .map { it.config[SemanticsProperties.Text].single().text }
            assertTrue("showed $files", "item-10.jpg" !in files)
        }
        showsFile(11)
    }

    @Test fun `tapping the photo while open hides the top bar but keeps the panel`() {
        open(10)
        openPanel()
        compose.onNodeWithTag("pager").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("top-bar").assertDoesNotExist()
        compose.onNodeWithTag("info-panel").assertIsDisplayed()
        compose.onNodeWithTag("film-strip").assertDoesNotExist()
    }

    @Test fun `opening the panel shows a hidden top bar`() {
        open(10)
        compose.onNodeWithTag("pager").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("top-bar").assertDoesNotExist()
        openPanel()
        compose.onNodeWithTag("top-bar").assertExists()
    }

    @Test fun `a zoomed photo ignores the swipe up`() {
        open(10)
        compose.onNodeWithTag("pager").performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(1_000)
        openPanel()
        compose.onNodeWithTag("info-panel").assertDoesNotExist()
    }

    @Test fun `scrolling the panel down past its top closes it`() {
        open(10)
        openPanel()
        compose.onNodeWithTag("info-panel").performTouchInput { swipeDown() }
        compose.waitForIdle()
        compose.onNodeWithTag("info-panel").assertDoesNotExist()
        assertEquals(emptyList<Long?>(), closed)
    }

    @Test fun `panel stays open when the shown item is removed`() {
        open(10)
        openPanel()
        items = hourly.filter { it.id != 10L }
        compose.waitForIdle()
        compose.onNodeWithTag("info-panel").assertIsDisplayed()
        showsFile(9)
    }

    @Test fun `the location card opens the map`() {
        open(10, recordingActions, info = FakeInfoSource(metadata = { pixelMetadata }, place = "Burg auf Fehmarn, Germany"))
        openPanel()
        compose.onNodeWithText("Burg auf Fehmarn, Germany", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("info-location").performClick()
        assertEquals(listOf(GeoPoint(54.5021, 11.2312)), shownOnMap)
    }

    @Test fun `without permission the panel asks through Show location`() {
        open(10, recordingActions, info = FakeInfoSource(metadata = { pixelMetadata }), locationGranted = false)
        openPanel()
        compose.onNodeWithTag("info-location").assertDoesNotExist()
        compose.onNodeWithTag("info-show-location").performClick()
        assertEquals(1, showLocationTaps)
    }
}
