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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
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
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val currentAnchor by rememberUpdatedState(anchor)
    // Building entries walks the whole timeline, so it stays off the main thread.
    val model by produceState<GridModel?>(null, items, level, labels) {
        val previous = value
        val followBottom = if (previous == null) currentAnchor.pinnedToBottom else !gridState.canScrollForward
        val grid = withContext(Dispatchers.Default) { TimelineGrid(items, level, labels.zone) }
        value = GridModel(items, level, grid, isFirst = previous == null, followBottom = followBottom)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val current = model ?: return@BoxWithConstraints
        if (current.items.isEmpty()) {
            Text("No photos yet", Modifier.align(Alignment.Center).testTag("empty"), color = Color.Gray)
            return@BoxWithConstraints
        }
        val columns = level.columnCount(maxWidth.value, LocalConfiguration.current.smallestScreenWidthDp.toFloat())
        val cellPx = with(LocalDensity.current) { (maxWidth / columns).roundToPx() }
        val thumbnailPx = thumbnailSizePx(cellPx)

        LaunchedEffect(current) { positionGrid(gridState, current, currentAnchor) }

        val newestVisible by remember(current) { derivedStateOf { newestVisibleItem(gridState, current) } }
        val reportAnchor by rememberUpdatedState(onAnchorChange)
        LaunchedEffect(current) {
            snapshotFlow { newestVisible to !gridState.canScrollForward }
                .distinctUntilChanged()
                .collect { (index, atBottom) ->
                    if (index != null) reportAnchor(GridAnchor(current.items[index].id, atBottom))
                }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = WindowInsets.systemBars.asPaddingValues(),
            horizontalArrangement = Arrangement.spacedBy(level.gapDp.dp),
            verticalArrangement = Arrangement.spacedBy(level.gapDp.dp),
            modifier = Modifier.fillMaxSize().testTag("grid"),
        ) {
            val entries = current.grid.entries
            items(
                count = entries.size,
                key = { index ->
                    when (val entry = entries[index]) {
                        is GridEntry.Header -> "h-${current.items[entry.firstItemIndex].id}"
                        is GridEntry.Cell -> current.items[entry.itemIndex].id
                    }
                },
                span = { index -> GridItemSpan(if (entries[index] is GridEntry.Header) maxLineSpan else 1) },
                contentType = { index -> entries[index]::class },
            ) { index ->
                when (val entry = entries[index]) {
                    is GridEntry.Header -> Text(
                        labels.header(level, current.items[entry.firstItemIndex].takenAt).orEmpty(),
                        Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                    is GridEntry.Cell -> {
                        val item = current.items[entry.itemIndex]
                        Cell(item, level, thumbnailPx, onClick = { onOpen(item.id) })
                    }
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

/** Puts the grid where [model] says: following the bottom, or at the anchored item on first show. */
private suspend fun positionGrid(state: LazyGridState, model: GridModel, anchor: GridAnchor) {
    val entries = model.grid.entries
    if (model.followBottom) {
        state.scrollToItem(entries.lastIndex)
        return
    }
    if (!model.isFirst) return
    val anchorIndex = model.items.indexOfFirst { it.id == anchor.itemId }
    if (anchorIndex >= 0) state.scrollToItem(model.grid.entryIndexOf(anchorIndex))
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
