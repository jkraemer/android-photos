package net.jkraemer.photos.ui.detail

import android.content.Context
import android.os.IInterface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.lang.reflect.Proxy
import java.time.Instant
import net.jkraemer.photos.core.MediaKind
import net.jkraemer.photos.item
import net.jkraemer.photos.useFakeImages
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowServiceManager

@RunWith(RobolectricTestRunner::class)
class VideoPageTest {
    @get:Rule val compose = createComposeRule()
    private val video = item(5, Instant.parse("2026-10-22T12:00:00Z"), MediaKind.VIDEO, durationMs = 65_000)
    private var isCurrent by mutableStateOf(true)

    @Before fun setUp() {
        registerMediaMetricsService()
        useFakeImages()
        compose.setContent { VideoPage(video, isCurrent, onTap = {}) }
    }

    @Test fun `video shows poster and play button`() {
        compose.onNodeWithTag("play").assertIsDisplayed()
        compose.onNodeWithTag("video-player").assertDoesNotExist()
    }

    @Test fun `play shows the player`() {
        compose.onNodeWithTag("play").performClick()
        compose.onNodeWithTag("video-player").assertExists()
    }

    @Test fun `leaving the page stops playback`() {
        compose.onNodeWithTag("play").performClick()
        isCurrent = false
        compose.waitForIdle()
        compose.onNodeWithTag("video-player").assertDoesNotExist()
        compose.onNodeWithTag("play").assertIsDisplayed()
    }

    /**
     * ExoPlayer looks up the platform media metrics service on every device;
     * Robolectric has none and prints a stack trace for the lookup. A no-op
     * service stands in for the device's, keeping the test output clean.
     */
    private fun registerMediaMetricsService() {
        @Suppress("UNCHECKED_CAST")
        val serviceClass = Class.forName("android.media.metrics.IMediaMetricsManager") as Class<out IInterface>
        val service = Proxy.newProxyInstance(serviceClass.classLoader, arrayOf(serviceClass)) { _, method, _ ->
            when (method.returnType) {
                String::class.java -> "test-session"
                else -> null
            }
        } as IInterface
        ShadowServiceManager.addBinderService(Context.MEDIA_METRICS_SERVICE, serviceClass, service)
    }
}
