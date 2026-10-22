package net.jkraemer.photos.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import net.jkraemer.photos.core.DateLabels
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.MediaKind
import net.jkraemer.photos.item
import net.jkraemer.photos.useFakeImages
import org.junit.Assert.assertEquals
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
    }

    @Before fun images() = useFakeImages()

    private var items by mutableStateOf(hourly)

    /** Records which item each button passed; the screen's wiring is what is under test. */
    private val shared = mutableListOf<Long>()
    private val trashed = mutableListOf<Long>()
    private val recordingActions = object : DetailActions {
        override fun share(item: MediaItem) { shared += item.id }
        override fun trash(item: MediaItem) { trashed += item.id }
    }

    private fun open(id: Long, actions: DetailActions = noActions) {
        compose.setContent {
            DetailScreen(items, id, labels, onClose = { closed += it }, actions = actions)
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

    @Test fun `strip shows five older and five newer`() {
        open(10)
        (5..15).forEach { compose.onNodeWithTag("strip-$it").assertExists() }
        compose.onNodeWithTag("strip-4").assertDoesNotExist()
        compose.onNodeWithTag("strip-16").assertDoesNotExist()
        compose.onNodeWithTag("strip-current").onChild().assert(hasTestTag("strip-10"))
    }

    @Test fun `strip at the oldest item has empty older slots`() {
        open(1)
        (1..6).forEach { compose.onNodeWithTag("strip-$it").assertExists() }
        compose.onAllNodesWithTag("strip-empty").assertCountEquals(5)
    }

    @Test fun `tapping a strip thumbnail jumps there`() {
        open(10)
        compose.onNodeWithTag("strip-13").performClick()
        compose.waitForIdle()
        showsItem(13)
    }

    @Test fun `dragging the strip left scrubs newer`() {
        open(10)
        // Slots are 36dp wide with 2dp gaps; at density 1 that is 38px each.
        compose.onNodeWithTag("film-strip").performTouchInput {
            swipe(start = center, end = center - Offset(3.3f * 38f, 0f), durationMillis = 400)
        }
        compose.waitForIdle()
        showsItem(13)
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
}
