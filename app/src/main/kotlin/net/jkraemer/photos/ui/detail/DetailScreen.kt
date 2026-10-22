package net.jkraemer.photos.ui.detail

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.distinctUntilChanged
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import net.jkraemer.photos.core.DateLabels
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.neighbourAfterRemoval
import net.jkraemer.photos.media.contentUriOf

/** What the detail view's buttons do with the item on screen. */
interface DetailActions {
    fun share(item: MediaItem)
    fun trash(item: MediaItem)
}

// A swipe down past this share of the screen height, or faster than this, closes the view.
private const val CLOSE_DISTANCE_FRACTION = 0.2f
private val CLOSE_VELOCITY = 1000.dp

/**
 * One item at a time over the whole timeline, older items to the left.
 * [onClose] gets the item on screen when the view is left, or null when the
 * timeline emptied underneath it.
 */
@Composable
fun DetailScreen(
    items: List<MediaItem>,
    initialId: Long,
    labels: DateLabels,
    onClose: (lastViewedId: Long?) -> Unit,
    actions: DetailActions,
    modifier: Modifier = Modifier,
) {
    var currentId by rememberSaveable { mutableLongStateOf(initialId) }
    val pagerState = rememberPagerState(initialPage = items.indexOfFirst { it.id == initialId }.coerceAtLeast(0)) {
        items.size
    }
    var uiVisible by rememberSaveable { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    val close by rememberUpdatedState(onClose)
    val latestItems by rememberUpdatedState(items)

    // The view follows its item by id: other apps (Immich freeing space, a
    // file manager) can add or remove items while it is open.
    var shownItems by remember { mutableStateOf(items) }
    LaunchedEffect(items) {
        val before = shownItems
        shownItems = items
        if (before === items) return@LaunchedEffect
        val target = if (items.any { it.id == currentId }) currentId else neighbourAfterRemoval(before, currentId, items)
        if (target == null) {
            close(null)
            return@LaunchedEffect
        }
        currentId = target
        pagerState.scrollToPage(items.indexOfFirst { it.id == target })
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page -> latestItems.getOrNull(page)?.let { currentId = it.id } }
    }

    BackHandler { close(currentId) }
    SystemBarsVisible(uiVisible)

    BoxWithConstraints(modifier.fillMaxSize()) {
        // Read through latestItems in here: the pager's page count updates
        // before this subcomposition does, and must never outrun the list.
        val current = latestItems.firstOrNull { it.id == currentId } ?: return@BoxWithConstraints
        var dragY by remember { mutableFloatStateOf(0f) }
        val heightPx = constraints.maxHeight.toFloat()
        val closeVelocityPx = with(LocalDensity.current) { CLOSE_VELOCITY.toPx() }

        HorizontalPager(
            state = pagerState,
            key = { latestItems[it].id },
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 1f - (dragY / heightPx).coerceIn(0f, 1f)))
                .draggable(
                    orientation = Orientation.Vertical,
                    enabled = !zoomed,
                    state = rememberDraggableState { delta -> dragY = (dragY + delta).coerceAtLeast(0f) },
                    onDragStopped = { velocity ->
                        if (dragY > heightPx * CLOSE_DISTANCE_FRACTION || velocity > closeVelocityPx) {
                            close(currentId)
                        } else {
                            animate(dragY, 0f) { value, _ -> dragY = value }
                        }
                    },
                )
                .graphicsLayer { translationY = dragY }
                .testTag("pager"),
        ) { page ->
            val isCurrent = page == pagerState.settledPage
            PhotoPage(
                latestItems[page],
                isCurrent,
                onTap = { uiVisible = !uiVisible },
                onZoomChange = { isZoomed ->
                    if (isCurrent) {
                        zoomed = isZoomed
                        if (isZoomed) uiVisible = false
                    }
                },
            )
        }

        if (uiVisible) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .testTag("top-bar"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { close(currentId) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(labels.timestamp(current.takenAt), Modifier.weight(1f).testTag("timestamp"), color = Color.White)
                IconButton(onClick = { actions.share(current) }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
                }
                IconButton(onClick = { actions.trash(current) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PhotoPage(item: MediaItem, isCurrent: Boolean, onTap: () -> Unit, onZoomChange: (Boolean) -> Unit) {
    val zoomState = rememberZoomableImageState(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 8f)))
    ZoomableAsyncImage(
        model = contentUriOf(item),
        contentDescription = null,
        state = zoomState,
        onClick = { onTap() },
        modifier = Modifier.fillMaxSize(),
    )
    val zoomChange by rememberUpdatedState(onZoomChange)
    LaunchedEffect(zoomState) {
        snapshotFlow { (zoomState.zoomableState.zoomFraction ?: 0f) > 0.01f }
            .distinctUntilChanged()
            .collect { zoomChange(it) }
    }
    // A page swiped away comes back unzoomed.
    LaunchedEffect(isCurrent) {
        if (!isCurrent) zoomState.zoomableState.resetZoom(animationSpec = SnapSpec())
    }
}

/** Shows or hides the status and navigation bars along with the view's own UI. */
@Composable
private fun SystemBarsVisible(visible: Boolean) {
    val view = LocalView.current
    DisposableEffect(visible) {
        val controller = view.context.findActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) controller?.show(WindowInsetsCompat.Type.systemBars())
        else controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
