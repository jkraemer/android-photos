package net.jkraemer.photos.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import net.jkraemer.photos.core.DateLabels
import net.jkraemer.photos.core.GeoPoint
import net.jkraemer.photos.core.InfoFormat
import net.jkraemer.photos.core.InfoText
import net.jkraemer.photos.core.MediaMetadata
import net.jkraemer.photos.pixelMetadata
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class InfoPanelTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val labels = DateLabels(Locale.UK, ZoneId.of("Europe/Berlin"))
    private val takenAt = Instant.parse("2026-10-22T08:00:00Z")
    private val format = InfoFormat(Locale.UK)
    private val shownOnMap = mutableListOf<GeoPoint>()
    private var showLocationTaps = 0

    private fun show(info: InfoText?, placeName: String? = null, locationGranted: Boolean = true) {
        compose.setContent {
            InfoPanel(
                info,
                placeName,
                locationGranted,
                onShowOnMap = { shownOnMap += it },
                onShowLocation = { showLocationTaps++ },
            )
        }
        compose.waitForIdle()
    }

    @Test fun `shows every line of a full photo`() {
        show(format.text(pixelMetadata, takenAt, labels), placeName = "Burg auf Fehmarn, Germany")
        compose.onNodeWithTag("info-date").assertTextEquals("Thursday · 22 Oct 2026 · 10:00")
        compose.onNodeWithTag("info-file").assertTextEquals("PXL_20261025_025836525.jpg")
        compose.onNodeWithTag("info-camera").assertTextEquals("Google Pixel 10 Pro")
        compose.onNodeWithTag("info-badge").assertTextEquals("JPEG")
        compose.onNodeWithTag("info-lens").assertTextEquals("Back camera — 24 mm ƒ1.68")
        compose.onNodeWithTag("info-dimensions").assertTextEquals("12.5 MP · 4080 × 3072 · 3.0 MB")
        for (entry in listOf("ISO 51", "24 mm", "0 ev", "ƒ1.68", "1/191 s")) {
            compose.onNodeWithText(entry, useUnmergedTree = true).assertExists()
        }
        compose.onNodeWithTag("info-exposure").assertExists()
        compose.onNodeWithTag("info-location").assertExists()
        compose.onNodeWithText("Burg auf Fehmarn, Germany", useUnmergedTree = true).assertExists()
    }

    @Test fun `missing fields leave their lines out`() {
        show(format.text(MediaMetadata("a.png", "image/png"), takenAt, labels))
        compose.onNodeWithTag("info-date").assertExists()
        compose.onNodeWithTag("info-file").assertTextEquals("a.png")
        compose.onNodeWithTag("info-badge").assertTextEquals("PNG")
        for (tag in listOf("info-camera", "info-lens", "info-dimensions", "info-exposure", "info-location", "info-show-location")) {
            compose.onNodeWithTag(tag).assertDoesNotExist()
        }
    }

    @Test fun `while reading it shows nothing`() {
        show(null)
        compose.onNodeWithTag("info-date").assertDoesNotExist()
        compose.onNodeWithTag("info-file").assertDoesNotExist()
    }

    @Test fun `location shows the place name and opens the map`() {
        show(format.text(pixelMetadata, takenAt, labels), placeName = "Burg auf Fehmarn, Germany")
        compose.onNodeWithTag("info-location").performClick()
        assertEquals(listOf(GeoPoint(54.5021, 11.2312)), shownOnMap)
    }

    @Test fun `location falls back to coordinates`() {
        show(format.text(pixelMetadata, takenAt, labels), placeName = null)
        compose.onNodeWithText("54.5021° N, 11.2312° E", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("info-location").performClick()
        assertEquals(listOf(GeoPoint(54.5021, 11.2312)), shownOnMap)
    }

    @Test fun `without permission it offers Show location`() {
        show(format.text(pixelMetadata.copy(location = null), takenAt, labels), locationGranted = false)
        compose.onNodeWithTag("info-location").assertDoesNotExist()
        compose.onNodeWithTag("info-show-location").performClick()
        compose.onNodeWithText("Show location", useUnmergedTree = true).assertExists()
        assertEquals(1, showLocationTaps)
    }

    @Test fun `granted without GPS shows no location at all`() {
        show(format.text(pixelMetadata.copy(location = null), takenAt, labels))
        compose.onNodeWithTag("info-location").assertDoesNotExist()
        compose.onNodeWithTag("info-show-location").assertDoesNotExist()
    }
}
