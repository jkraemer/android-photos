package net.jkraemer.photos.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.filmStripWindow
import net.jkraemer.photos.core.scrubTarget
import net.jkraemer.photos.media.MediaThumbnail
import net.jkraemer.photos.media.contentUriOf
import net.jkraemer.photos.media.thumbnailSizePx

private val SLOT_WIDTH = 36.dp
private val SLOT_HEIGHT = 48.dp
private val SLOT_GAP = 2.dp
private val CURRENT_WIDTH = 48.dp
private val CURRENT_HEIGHT = 56.dp

/**
 * The current item (index [current] in [items]) in its timeline context:
 * five older thumbnails to the left, five newer to the right. Tapping a
 * thumbnail calls [onSelect]; dragging the strip calls [onScrub] with each
 * new target, one item per slot width, dragging left going newer.
 */
@Composable
fun FilmStrip(
    items: List<MediaItem>,
    current: Int,
    onSelect: (Int) -> Unit,
    onScrub: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slotPitchPx = with(LocalDensity.current) { (SLOT_WIDTH + SLOT_GAP).toPx() }
    val thumbnailPx = thumbnailSizePx(with(LocalDensity.current) { CURRENT_HEIGHT.roundToPx() })
    val latestCurrent by rememberUpdatedState(current)
    var dragStart by remember { mutableIntStateOf(0) }
    var dragTotal by remember { mutableFloatStateOf(0f) }
    var lastTarget by remember { mutableIntStateOf(0) }

    Row(
        modifier
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    dragTotal += delta
                    val target = scrubTarget(dragStart, dragTotal, slotPitchPx, items.size)
                    if (target != lastTarget) {
                        lastTarget = target
                        onScrub(target)
                    }
                },
                onDragStarted = {
                    dragStart = latestCurrent
                    lastTarget = latestCurrent
                    dragTotal = 0f
                },
            )
            .testTag("film-strip"),
        horizontalArrangement = Arrangement.spacedBy(SLOT_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        filmStripWindow(current, items.size).forEach { index ->
            if (index == null) {
                Box(Modifier.size(SLOT_WIDTH, SLOT_HEIGHT).testTag("strip-empty"))
            } else if (index == current) {
                Box(
                    Modifier
                        .size(CURRENT_WIDTH, CURRENT_HEIGHT)
                        .border(2.dp, Color.White)
                        .testTag("strip-current"),
                ) {
                    Thumbnail(items[index], thumbnailPx, Modifier.size(CURRENT_WIDTH, CURRENT_HEIGHT))
                }
            } else {
                Thumbnail(
                    items[index],
                    thumbnailPx,
                    Modifier.size(SLOT_WIDTH, SLOT_HEIGHT).clickable { onSelect(index) },
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(item: MediaItem, sizePx: Int, modifier: Modifier) {
    AsyncImage(
        model = MediaThumbnail(contentUriOf(item), sizePx),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(Color.DarkGray).testTag("strip-${item.id}"),
    )
}
