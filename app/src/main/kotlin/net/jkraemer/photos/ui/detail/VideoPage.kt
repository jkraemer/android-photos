package net.jkraemer.photos.ui.detail

import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.media.MediaThumbnail
import net.jkraemer.photos.media.contentUriOf
import net.jkraemer.photos.media.thumbnailSizePx

/**
 * A video in the detail view: its poster frame with a play button until
 * played, then an inline player with sound and the standard controls. A tap
 * toggles [uiVisible], which the player's controls follow. The player is
 * released as soon as the page stops being the current one.
 */
@Composable
fun VideoPage(item: MediaItem, isCurrentPage: Boolean, uiVisible: Boolean, onUiVisibleChange: (Boolean) -> Unit) {
    var playing by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) playing = false
    }
    // Leaving the app (home, app switcher, screen off) only stops the
    // activity; the composition survives, so playback has to end here.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) playing = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = null, indication = null) { onUiVisibleChange(!uiVisible) }
            .testTag("video-page"),
    ) {
        if (playing) {
            val context = LocalContext.current
            val player = remember {
                ExoPlayer.Builder(context)
                    // Takes audio focus, so a video pauses music instead of playing over it.
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build(),
                        true,
                    )
                    .build()
                    .apply {
                        setMediaItem(PlayerMediaItem.fromUri(contentUriOf(item)))
                        volume = 1f
                        prepare()
                        play()
                    }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            // The player takes every tap on itself, so its controls and the
            // detail view's own UI show and hide as one: a tap or the
            // controls' own timeout reports here, and the shared state drives
            // the controls back.
            val shownNow by rememberUpdatedState(uiVisible)
            val reportShown by rememberUpdatedState(onUiVisibleChange)
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        this.player = player
                        setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { visibility ->
                                val shown = visibility == View.VISIBLE
                                if (shown != shownNow) reportShown(shown)
                            },
                        )
                    }
                },
                update = { view -> if (uiVisible) view.showController() else view.hideController() },
                modifier = Modifier.fillMaxSize().testTag("video-player"),
            )
        } else {
            val posterPx = with(LocalDensity.current) { maxOf(maxWidth, maxHeight).roundToPx() }
            AsyncImage(
                model = MediaThumbnail(contentUriOf(item), thumbnailSizePx(posterPx)),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            FilledIconButton(
                onClick = { playing = true },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                modifier = Modifier.align(Alignment.Center).size(72.dp).testTag("play"),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }
    }
}
