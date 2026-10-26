package net.jkraemer.photos.ui.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class PinchToZoomTest {
    @get:Rule val compose = createComposeRule()

    private val steps = mutableListOf<Boolean>()

    private fun showPinchable() = compose.setContent {
        Box(Modifier.fillMaxSize().testTag("area").pinchToZoom { zoomIn, _ -> steps += zoomIn })
    }

    private fun pinch(from: Float, to: Float) = compose.onNodeWithTag("area").performTouchInput {
        pinch(
            start0 = center - Offset(from, 0f), end0 = center - Offset(to, 0f),
            start1 = center + Offset(from, 0f), end1 = center + Offset(to, 0f),
            durationMillis = 400,
        )
    }

    @Test fun `a long pinch together steps one level`() {
        showPinchable()
        pinch(from = 180f, to = 30f)
        compose.waitForIdle()
        assertEquals(listOf(false), steps)
    }

    @Test fun `a long spread steps one level`() {
        showPinchable()
        pinch(from = 30f, to = 180f)
        compose.waitForIdle()
        assertEquals(listOf(true), steps)
    }

    @Test fun `each pinch steps again`() {
        showPinchable()
        pinch(from = 180f, to = 30f)
        pinch(from = 180f, to = 30f)
        compose.waitForIdle()
        assertEquals(listOf(false, false), steps)
    }
}
