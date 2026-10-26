package net.jkraemer.photos.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import net.jkraemer.photos.GridAnchor
import net.jkraemer.photos.core.DateLabels
import net.jkraemer.photos.core.GridEntry
import net.jkraemer.photos.core.Headline
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.MediaKind
import net.jkraemer.photos.core.TimelineGrid
import net.jkraemer.photos.core.VisibleCell
import net.jkraemer.photos.core.ZoomLevel
import net.jkraemer.photos.core.newestFullyVisible
import net.jkraemer.photos.media.MediaThumbnail
import net.jkraemer.photos.media.contentUriOf
import net.jkraemer.photos.media.thumbnailSizePx

/**
 * The items and grid entries the screen shows, built together so the grid
 * never indexes into a different list. [isFirst] marks the model the screen
 * opens with; [followBottom] says whether the grid should sit at the bottom.
 */
private class GridModel(
    val items: List<MediaItem>,
    val level: ZoomLevel,
    val grid: TimelineGrid,
    val isFirst: Boolean,
    val followBottom: Boolean,
)

/** The item under a pinch and its row's offset in the viewport, to restore after the level changes. */
private class PinchFocus(val itemIndex: Int, val offsetY: Int)

/**
 * The camera roll as a vertical timeline, oldest top-left and newest
 * bottom-right, at [level]. Opens at [anchor] and reports its position back
 * through [onAnchorChange] as the user scrolls.
 */
@Composable
fun GridScreen(
    items: List<MediaItem>,
    level: ZoomLevel,
    labels: DateLabels,
    anchor: GridAnchor,
    onAnchorChange: (GridAnchor) -> Unit,
    onLevelChange: (ZoomLevel) -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val currentAnchor by rememberUpdatedState(anchor)
    val pinchFocus = remember { mutableStateOf<PinchFocus?>(null) }
    // The anchor this grid reported itself; when it comes back through the
    // caller it describes where the grid already is (or just was, mid-fling).
    val lastReported = remember { mutableStateOf<GridAnchor?>(null) }
    // Building entries walks the whole timeline, so it stays off the main thread.
    val model by produceState<GridModel?>(null, items, level, labels) {
        val previous = value
        val followBottom = if (previous == null) currentAnchor.pinnedToBottom else !gridState.canScrollForward
        val grid = withContext(Dispatchers.Default) { TimelineGrid(items, level, labels.zone) }
        value = GridModel(items, level, grid, isFirst = previous == null, followBottom = followBottom)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val current = model ?: return@BoxWithConstraints
        // Layout follows the model's level, not the requested one: switching
        // columns before the new entries exist would scroll the old grid and
        // lose the position the new model is about to restore.
        val level = current.level
        if (current.items.isEmpty()) {
            Text("No photos yet", Modifier.align(Alignment.Center).testTag("empty"), color = Color.Gray)
            return@BoxWithConstraints
        }
        val columns = level.columnCount(maxWidth.value, LocalConfiguration.current.smallestScreenWidthDp.toFloat())
        val cellPx = with(LocalDensity.current) { (maxWidth / columns).roundToPx() }
        val thumbnailPx = thumbnailSizePx(cellPx)

        LaunchedEffect(current) {
            positionGrid(gridState, current, currentAnchor, pinchFocus.value)
            pinchFocus.value = null
        }
        val onPinchStep by rememberUpdatedState { zoomIn: Boolean, focal: Offset ->
            val next = if (zoomIn) level.closer() else level.farther()
            if (next != level) {
                pinchFocus.value = cellUnder(gridState, current, focal)
                onLevelChange(next)
            }
        }

        val newestVisible by remember(current) { derivedStateOf { newestVisibleItem(gridState, current) } }
        val reportAnchor by rememberUpdatedState(onAnchorChange)
        LaunchedEffect(current) {
            snapshotFlow { newestVisible to !gridState.canScrollForward }
                .distinctUntilChanged()
                .collect { (index, atBottom) ->
                    if (index != null) {
                        val report = GridAnchor(current.items[index].id, atBottom)
                        lastReported.value = report
                        reportAnchor(report)
                    }
                }
        }
        LaunchedEffect(anchor) {
            if (anchor != lastReported.value) revealAnchor(gridState, current, anchor)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = WindowInsets.systemBars.asPaddingValues(),
            horizontalArrangement = Arrangement.spacedBy(level.gapDp.dp),
            verticalArrangement = Arrangement.spacedBy(level.gapDp.dp),
            modifier = Modifier
                .fillMaxSize()
                .pinchToZoom { zoomIn, focal -> onPinchStep(zoomIn, focal) }
                .testTag("grid"),
        ) {
            // A header is a near-empty row: it still starts its month or year on
            // a new row, while its label floats over the photos below it and
            // sticks to the top until the next header takes over. The row is
            // 1dp, not 0: on the device a zero-height item draws the label's
            // backing but not its text. The cells
            // between two headers form one run, so the grid's item indices stay
            // those of the entries.
            val entries = current.grid.entries
            var start = 0
            while (start < entries.size) {
                val entry = entries[start]
                if (entry is GridEntry.Header) {
                    stickyHeader(key = "h-${current.items[entry.firstItemIndex].id}", contentType = GridEntry.Header::class) {
                        Box(Modifier.fillMaxWidth().height(1.dp).zIndex(1f).wrapContentHeight(Alignment.Top, unbounded = true)) {
                            HeaderLabel(labels.header(level, current.items[entry.firstItemIndex].takenAt)!!)
                        }
                    }
                    start++
                    continue
                }
                val first = start
                while (start < entries.size && entries[start] is GridEntry.Cell) start++
                items(
                    count = start - first,
                    key = { current.items[(entries[first + it] as GridEntry.Cell).itemIndex].id },
                    contentType = { GridEntry.Cell::class },
                ) { offset ->
                    val item = current.items[(entries[first + offset] as GridEntry.Cell).itemIndex]
                    Cell(item, level, thumbnailPx, onClick = { onOpen(item.id) })
                }
            }
        }

        val floating = newestVisible?.let { labels.floating(level, current.items[it].takenAt) }
        if (floating != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    floating,
                    Modifier.testTag("floating-date"),
                    style = TextStyle(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        shadow = Shadow(Color.Black, blurRadius = 8f),
                    ),
                )
            }
        }
    }
}

/** A month or year headline; its half-transparent backing keeps it readable over the photos. */
@Composable
private fun HeaderLabel(headline: Headline) {
    Box(Modifier.padding(start = 4.dp, top = 4.dp)) {
        Text(
            buildAnnotatedString {
                append(headline.name)
                headline.year?.let {
                    append(" ")
                    withStyle(SpanStyle(color = Color.LightGray)) { append(it) }
                }
            },
            Modifier
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
        )
    }
}

@Composable
private fun Cell(item: MediaItem, level: ZoomLevel, thumbnailPx: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .background(Color.DarkGray)
            .clickable(onClick = onClick)
            .testTag("cell-${item.id}"),
    ) {
        AsyncImage(
            model = MediaThumbnail(contentUriOf(item), thumbnailPx),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.kind == MediaKind.VIDEO && level.showsVideoDuration) {
            Text(
                formatDuration(item.durationMs),
                Modifier.align(Alignment.BottomEnd).padding(4.dp),
                style = TextStyle(color = Color.White, fontSize = 12.sp, shadow = Shadow(Color.Black, blurRadius = 4f)),
            )
        }
    }
}

/**
 * Puts the grid where [model] says: following the bottom, back under the
 * fingers after a pinch, or at the anchored item on first show.
 */
private suspend fun positionGrid(state: LazyGridState, model: GridModel, anchor: GridAnchor, focus: PinchFocus?) {
    val entries = model.grid.entries
    if (model.followBottom) {
        state.scrollToItem(entries.lastIndex)
        return
    }
    if (focus != null && focus.itemIndex < model.items.size) {
        state.scrollToItem(model.grid.entryIndexOf(focus.itemIndex))
        state.scrollBy(-focus.offsetY.toFloat())
        return
    }
    if (!model.isFirst) return
    val anchorIndex = model.items.indexOfFirst { it.id == anchor.itemId }
    if (anchorIndex >= 0) state.scrollToItem(model.grid.entryIndexOf(anchorIndex))
}

/** Brings an anchor set from outside (e.g. the item last viewed in detail) on screen if it is not. */
private suspend fun revealAnchor(state: LazyGridState, model: GridModel, anchor: GridAnchor) {
    if (anchor.pinnedToBottom) {
        if (state.canScrollForward) state.scrollToItem(model.grid.entries.lastIndex)
        return
    }
    val itemIndex = model.items.indexOfFirst { it.id == anchor.itemId }
    if (itemIndex < 0) return
    val entryIndex = model.grid.entryIndexOf(itemIndex)
    val layout = state.layoutInfo
    val info = layout.visibleItemsInfo.firstOrNull { it.index == entryIndex }
    val fullyVisible = info != null &&
        info.offset.y >= layout.viewportStartOffset &&
        info.offset.y + info.size.height <= layout.viewportEndOffset
    if (!fullyVisible) state.scrollToItem(entryIndex)
}

/** The laid-out cell nearest to [focal] (in grid coordinates), with its row offset. */
private fun cellUnder(state: LazyGridState, model: GridModel, focal: Offset): PinchFocus? {
    val layout = state.layoutInfo
    return layout.visibleItemsInfo
        .mapNotNull { info ->
            val cell = model.grid.entries.getOrNull(info.index) as? GridEntry.Cell ?: return@mapNotNull null
            // Item offsets start after the top content padding; the pointer's do not.
            val top = info.offset.y - layout.viewportStartOffset
            val centre = Offset(info.offset.x + info.size.width / 2f, top + info.size.height / 2f)
            Triple(cell.itemIndex, info.offset.y, (centre - focal).getDistanceSquared())
        }
        .minByOrNull { it.third }
        ?.let { (itemIndex, offsetY, _) -> PinchFocus(itemIndex, offsetY) }
}

private fun newestVisibleItem(state: LazyGridState, model: GridModel): Int? {
    val layout = state.layoutInfo
    val cells = layout.visibleItemsInfo.mapNotNull { info ->
        (model.grid.entries.getOrNull(info.index) as? GridEntry.Cell)?.let {
            VisibleCell(it.itemIndex, info.offset.y, info.offset.y + info.size.height)
        }
    }
    return newestFullyVisible(cells, layout.viewportStartOffset, layout.viewportEndOffset)
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
