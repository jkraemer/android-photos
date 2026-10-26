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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.request.ImageRequest
import coil3.request.Options
import kotlinx.coroutines.flow.distinctUntilChanged
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import net.jkraemer.photos.core.DateLabels
import net.jkraemer.photos.core.GeoPoint
import net.jkraemer.photos.core.InfoFormat
import net.jkraemer.photos.core.InfoText
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.MediaKind
import net.jkraemer.photos.core.MediaMetadata
import net.jkraemer.photos.core.neighbourAfterRemoval
import net.jkraemer.photos.core.panelOpenAfterDrag
import net.jkraemer.photos.media.MediaThumbnailKeyer
import net.jkraemer.photos.media.contentUriOf

/** What the detail view's buttons do with the item on screen. */
interface DetailActions {
    fun share(item: MediaItem)
    fun trash(item: MediaItem)
    fun showOnMap(point: GeoPoint)

    /** The info panel settled open; the place to ask for the location permission. */
    fun panelOpened()

    /** The panel's "Show location" row was tapped. */
    fun showLocation()
}

/** Where the info panel gets an item's metadata and a place's name. */
interface InfoSource {
    suspend fun metadata(item: MediaItem, withLocation: Boolean): MediaMetadata
    suspend fun placeName(point: GeoPoint): String?
}

// A swipe down past this share of the screen height, or faster than this, closes the view.
private const val CLOSE_DISTANCE_FRACTION = 0.2f
private val CLOSE_VELOCITY = 1000.dp

// A swipe up past this share of the screen height, or as fast as a closing one, opens the info panel.
private const val PANEL_DISTANCE_FRACTION = 0.15f

// The photo's share of the screen while the panel is out: its height in portrait, its width in landscape.
private const val PHOTO_SHARE_WITH_PANEL = 0.45f
private const val PHOTO_SHARE_WITH_PANEL_LANDSCAPE = 0.5f

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
    info: InfoSource,
    locationGranted: Boolean,
    modifier: Modifier = Modifier,
) {
    var currentId by rememberSaveable { mutableLongStateOf(initialId) }
    val pagerState = rememberPagerState(initialPage = items.indexOfFirst { it.id == initialId }.coerceAtLeast(0)) {
        items.size
    }
    var uiVisible by rememberSaveable { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    // How far the info panel is out: 0 shut, 1 open, in between while dragged or settling.
    var panelFraction by remember { mutableFloatStateOf(if (panelOpen) 1f else 0f) }
    val close by rememberUpdatedState(onClose)
    val latestItems by rememberUpdatedState(items)

    // The view follows its item by id: other apps (Immich freeing space, a
    // file manager) can add or remove items while it is open. The item to
    // show is resolved during composition, so the frame that first sees the
    // new list already shows the right item instead of a gap or a stranger.
    val previousItems = remember { arrayOf(items) }
    val shownId: Long? =
        if (items.any { it.id == currentId }) currentId
        else neighbourAfterRemoval(previousItems[0], currentId, items)
    if (items !== previousItems[0] && shownId != null) {
        pagerState.requestScrollToPage(items.indexOfFirst { it.id == shownId })
    }
    SideEffect {
        previousItems[0] = items
        if (shownId != null) currentId = shownId
    }
    LaunchedEffect(shownId == null) {
        if (shownId == null) close(null)
    }
    val latestShownId by rememberUpdatedState(shownId)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page -> latestItems.getOrNull(page)?.let { currentId = it.id } }
    }

    /** Slides the panel open or shut. Opening brings the top bar back with it. */
    suspend fun settlePanel(open: Boolean) {
        if (open && !panelOpen) {
            uiVisible = true
            actions.panelOpened()
        }
        panelOpen = open
        animate(panelFraction, if (open) 1f else 0f) { value, _ -> panelFraction = value }
    }

    BackHandler { close(currentId) }
    SystemBarsVisible(uiVisible)

    BoxWithConstraints(modifier.fillMaxSize()) {
        // Read through latestItems in here: the pager's page count updates
        // before this subcomposition does, and must never outrun the list.
        val current = latestItems.firstOrNull { it.id == latestShownId }
        if (current == null) {
            // Everything is gone; the view is closing.
            Box(Modifier.fillMaxSize().background(Color.Black))
            return@BoxWithConstraints
        }
        var dragY by remember { mutableFloatStateOf(0f) }
        val heightPx = constraints.maxHeight.toFloat()
        val closeVelocityPx = with(LocalDensity.current) { CLOSE_VELOCITY.toPx() }
        val landscape = constraints.maxWidth > constraints.maxHeight
        // A vertical drag over this distance takes the panel from shut to open.
        val panelTravelPx = heightPx * (1f - PHOTO_SHARE_WITH_PANEL)

        suspend fun settlePanelAfterDrag(velocity: Float) {
            val dragPx = if (panelOpen) (1f - panelFraction) * panelTravelPx else -panelFraction * panelTravelPx
            settlePanel(panelOpenAfterDrag(panelOpen, dragPx, velocity, heightPx * PANEL_DISTANCE_FRACTION, closeVelocityPx))
        }

        // Pulling the panel's content down at its top slides the panel shut;
        // pushing it back up reopens the panel before the content scrolls.
        val panelOverscroll = object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f || panelFraction >= 1f) return Offset.Zero
                val before = panelFraction
                panelFraction = (panelFraction - available.y / panelTravelPx).coerceAtMost(1f)
                return Offset(0f, -(panelFraction - before) * panelTravelPx)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                panelFraction = (panelFraction - available.y / panelTravelPx).coerceAtLeast(0f)
                // Pulled all the way shut, the panel leaves composition before
                // any fling arrives to settle it, so it is closed here.
                if (panelFraction == 0f) panelOpen = false
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (panelFraction >= 1f) return Velocity.Zero
                settlePanelAfterDrag(available.y)
                return available
            }
        }

        // Read only while the panel shows. Each result carries its item's id, so
        // a read that finishes after a swipe to the next item never shows there.
        val panelShown = panelFraction > 0f
        val format = remember(labels) { InfoFormat(labels.locale) }
        val read by produceState<Pair<Long, InfoText>?>(null, current.id, locationGranted, panelShown) {
            if (panelShown) value = current.id to format.text(info.metadata(current, locationGranted), current.takenAt, labels)
        }
        val shownInfo = read?.takeIf { it.first == current.id }?.second
        val location = shownInfo?.location
        val place by produceState<Pair<GeoPoint, String?>?>(null, location) {
            if (location != null) value = location to info.placeName(location)
        }
        val placeName = place?.takeIf { it.first == location }?.second

        val pagerPane: @Composable (Modifier) -> Unit = { paneModifier ->
            HorizontalPager(
                state = pagerState,
                key = { latestItems[it].id },
                userScrollEnabled = !zoomed,
                beyondViewportPageCount = 1,
                modifier = paneModifier
                    .background(Color.Black.copy(alpha = 1f - (dragY / heightPx).coerceIn(0f, 1f)))
                    .draggable(
                        orientation = Orientation.Vertical,
                        enabled = !zoomed,
                        state = rememberDraggableState { delta ->
                            // Downwards from the plain view closes it; everything else moves the panel.
                            if (dragY > 0f || (!panelOpen && panelFraction == 0f && delta > 0f)) {
                                dragY = (dragY + delta).coerceAtLeast(0f)
                            } else {
                                panelFraction = (panelFraction - delta / panelTravelPx).coerceIn(0f, 1f)
                            }
                        },
                        onDragStopped = { velocity ->
                            if (dragY > 0f) {
                                if (dragY > heightPx * CLOSE_DISTANCE_FRACTION || velocity > closeVelocityPx) {
                                    close(currentId)
                                } else {
                                    animate(dragY, 0f) { value, _ -> dragY = value }
                                }
                            } else {
                                settlePanelAfterDrag(velocity)
                            }
                        },
                    )
                    .graphicsLayer { translationY = dragY }
                    .testTag("pager"),
            ) { page ->
                val isCurrent = page == pagerState.settledPage
                val item = latestItems[page]
                when (item.kind) {
                    MediaKind.IMAGE -> PhotoPage(
                        item,
                        isCurrent,
                        onTap = { uiVisible = !uiVisible },
                        onZoomChange = { isZoomed ->
                            if (isCurrent) {
                                zoomed = isZoomed
                                if (isZoomed) uiVisible = false
                            }
                        },
                    )
                    MediaKind.VIDEO -> VideoPage(item, isCurrent, onTap = { uiVisible = !uiVisible })
                }
            }
        }
        val panelPane: @Composable (Modifier) -> Unit = { paneModifier ->
            if (panelShown) {
                Column(
                    paneModifier
                        .background(Color.Black)
                        .nestedScroll(panelOverscroll)
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("info-panel"),
                ) {
                    InfoPanel(
                        shownInfo,
                        placeName,
                        locationGranted,
                        onShowOnMap = actions::showOnMap,
                        onShowLocation = actions::showLocation,
                    )
                }
            }
        }

        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                pagerPane(Modifier.fillMaxHeight().fillMaxWidth(1f - panelFraction * (1f - PHOTO_SHARE_WITH_PANEL_LANDSCAPE)))
                // The top bar spans the whole width, so the panel starts below it.
                panelPane(Modifier.weight(1f).fillMaxHeight().windowInsetsPadding(WindowInsets.statusBars).padding(top = 48.dp))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                pagerPane(Modifier.fillMaxWidth().fillMaxHeight(1f - panelFraction * (1f - PHOTO_SHARE_WITH_PANEL)))
                panelPane(Modifier.weight(1f).fillMaxWidth())
            }
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
            // The panel takes the film strip's place while it is out.
            if (!panelShown) {
                FilmStrip(
                    latestItems,
                    pagerState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun PhotoPage(item: MediaItem, isCurrent: Boolean, onTap: () -> Unit, onZoomChange: (Boolean) -> Unit) {
    val zoomState = rememberZoomableImageState(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 8f)))
    ZoomableAsyncImage(
        model = photoRequest(LocalContext.current, item, LocalDensity.current),
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

/**
 * The full-size image of [item], showing the film strip's thumbnail from the
 * memory cache until it loads: scrubbing the strip passes photos faster than
 * they decode, and the view would otherwise stay black.
 */
internal fun photoRequest(context: Context, item: MediaItem, density: Density): ImageRequest =
    ImageRequest.Builder(context)
        .data(contentUriOf(item))
        .placeholderMemoryCacheKey(MediaThumbnailKeyer().key(stripThumbnail(item, density), Options(context)))
        .build()

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
