package net.jkraemer.photos.ui.grid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertTextEquals
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import net.jkraemer.photos.GridAnchor
import net.jkraemer.photos.core.DateLabels
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.MediaKind
import net.jkraemer.photos.core.ZoomLevel
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
class GridScreenTest {
    @get:Rule val compose = createComposeRule()
    private val labels = DateLabels(Locale.UK, ZoneId.of("Europe/Berlin"))
    private val newest = Instant.parse("2026-10-22T12:00:00Z")
    private val bottom = GridAnchor(itemId = null, pinnedToBottom = true)

    /** [count] photos one day apart, ids 1 (oldest) to [count] (taken [newest]). */
    private fun daily(count: Int): List<MediaItem> =
        (1..count).map { item(it.toLong(), newest.minus(Duration.ofDays((count - it).toLong()))) }

    @Before fun images() = useFakeImages()

    private fun show(
        items: List<MediaItem>,
        level: ZoomLevel = ZoomLevel.LARGE,
        anchor: GridAnchor = bottom,
        onOpen: (Long) -> Unit = {},
    ) {
        compose.setContent {
            GridScreen(items, level, labels, anchor, onAnchorChange = {}, onOpen = onOpen)
        }
        awaitGrid()
    }

    /** The grid is built off the main thread, which the test clock does not track. */
    private fun awaitGrid() = compose.waitUntil(5_000) {
        compose.onAllNodes(hasTestTag("grid")).fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodes(hasTestTag("empty")).fetchSemanticsNodes().isNotEmpty()
    }

    private fun awaitTag(tag: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun `opens at the newest items`() {
        show(daily(200))
        compose.onNodeWithTag("cell-200").assertIsDisplayed()
        compose.onNodeWithTag("cell-1").assertDoesNotExist()
    }

    @Test fun `floating label shows the newest fully visible day`() {
        show(daily(200))
        compose.onNodeWithTag("floating-date").assertTextEquals("22 Oct 2026")
    }

    @Test fun `MEDIUM floats the month`() {
        show(daily(200), ZoomLevel.MEDIUM)
        compose.onNodeWithTag("floating-date").assertTextEquals("October 2026")
    }

    @Test fun `SMALL shows month headers and no floating label`() {
        show(daily(200), ZoomLevel.SMALL)
        compose.onNodeWithText("October 2026").assertIsDisplayed()
        compose.onNodeWithTag("floating-date").assertDoesNotExist()
    }

    @Test fun `TINY shows year headers`() {
        show(daily(400), ZoomLevel.TINY)
        compose.onNodeWithText("2026").assertIsDisplayed()
        compose.onNodeWithText("2025").assertIsDisplayed()
        compose.onNodeWithTag("floating-date").assertDoesNotExist()
    }

    @Test fun `stays at the bottom when a new photo arrives`() {
        var items by mutableStateOf(daily(200))
        compose.setContent { GridScreen(items, ZoomLevel.LARGE, labels, bottom, onAnchorChange = {}, onOpen = {}) }
        awaitGrid()
        // Ten new photos start rows below the old bottom edge.
        items = items + (201L..210L).map { item(it, newest.plusSeconds(it)) }
        awaitTag("cell-210")
        compose.onNodeWithTag("cell-210").assertIsDisplayed()
    }

    @Test fun `keeps position when scrolled up and a photo arrives`() {
        var items by mutableStateOf(daily(200))
        compose.setContent { GridScreen(items, ZoomLevel.LARGE, labels, bottom, onAnchorChange = {}, onOpen = {}) }
        awaitGrid()
        compose.onNodeWithTag("grid").performScrollToIndex(0)
        items = items + item(201, newest.plusSeconds(60))
        compose.waitForIdle()
        compose.onNodeWithTag("cell-1").assertIsDisplayed()
        compose.onNodeWithTag("cell-201").assertDoesNotExist()
    }

    @Test fun `opens at the anchored item`() {
        show(daily(200), anchor = GridAnchor(itemId = 100, pinnedToBottom = false))
        compose.onNodeWithTag("cell-100").assertIsDisplayed()
        compose.onNodeWithTag("cell-200").assertDoesNotExist()
    }

    @Test fun `empty timeline shows the empty state`() {
        show(emptyList())
        compose.onNodeWithText("No photos yet").assertIsDisplayed()
        compose.onNodeWithTag("floating-date").assertDoesNotExist()
    }

    @Test fun `video shows its duration at LARGE`() {
        show(daily(10) + item(11, newest.plusSeconds(60), MediaKind.VIDEO, durationMs = 65_000))
        compose.onNodeWithText("1:05").assertIsDisplayed()
    }

    @Test fun `video duration is hidden at SMALL`() {
        show(daily(10) + item(11, newest.plusSeconds(60), MediaKind.VIDEO, durationMs = 65_000), ZoomLevel.SMALL)
        compose.onNodeWithTag("cell-11").assertIsDisplayed()
        compose.onNodeWithText("1:05").assertDoesNotExist()
    }

    @Test fun `tapping a cell opens it`() {
        var opened: Long? = null
        show(daily(20), onOpen = { opened = it })
        compose.onNodeWithTag("cell-20").performClick()
        assertEquals(20L, opened)
    }
}
