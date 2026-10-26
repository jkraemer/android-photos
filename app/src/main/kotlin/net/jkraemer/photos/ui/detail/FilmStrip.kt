package net.jkraemer.photos.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.centredItem
import net.jkraemer.photos.core.stripScrollFor
import net.jkraemer.photos.media.MediaThumbnail
import net.jkraemer.photos.media.contentUriOf
import net.jkraemer.photos.media.thumbnailSizePx

private val SLOT_WIDTH = 36.dp
private val SLOT_HEIGHT = 48.dp
private val SLOT_GAP = 2.dp

/** Which side moves the other: the strip and the pager each follow whoever the user is moving. */
private enum class Lead {
    /** The photo is swiped (or still); the strip follows it slot for page. */
    PAGER,

    /** The strip is dragged or flinging; the photo follows its centre thumbnail. */
    STRIP,

    /** The strip slides to a tapped thumbnail that the photo already shows. */
    TAP,
}

/**
 * The whole timeline as a row of thumbnails with the photo [pagerState]
 * shows centred and marked. The strip moves with the photo as it is swiped;
 * dragging or flinging the strip scrubs the photo along, and tapping a
 * thumbnail shows that photo at once while the strip slides it to the centre.
 */
@Composable
fun FilmStrip(items: List<MediaItem>, pagerState: PagerState, modifier: Modifier = Modifier) {
    val pitchPx = with(LocalDensity.current) { (SLOT_WIDTH + SLOT_GAP).toPx() }
    val thumbnailPx = thumbnailSizePx(with(LocalDensity.current) { SLOT_HEIGHT.roundToPx() })
    val stripState = rememberLazyListState(initialFirstVisibleItemIndex = pagerState.currentPage)
    val scope = rememberCoroutineScope()
    var lead by remember { mutableStateOf(Lead.PAGER) }
    val latestItems by rememberUpdatedState(items)

    LaunchedEffect(stripState) {
        stripState.interactionSource.interactions.collect { if (it is DragInteraction.Start) lead = Lead.STRIP }
    }
    LaunchedEffect(stripState) {
        snapshotFlow { stripState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && lead == Lead.STRIP) lead = Lead.PAGER
        }
    }
    LaunchedEffect(stripState, pagerState) {
        snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }.collect {
            if (lead == Lead.PAGER) {
                val scroll = stripScrollFor(pagerState.currentPage, pagerState.currentPageOffsetFraction, pitchPx)
                stripState.scrollToItem(scroll.index, scroll.offsetPx)
            }
        }
    }
    LaunchedEffect(stripState, pagerState) {
        snapshotFlow {
            centredItem(stripState.firstVisibleItemIndex, stripState.firstVisibleItemScrollOffset, pitchPx, latestItems.size)
        }.collect { centred ->
            if (lead == Lead.STRIP && centred != pagerState.currentPage) pagerState.scrollToPage(centred)
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Padding of half the free width lets the first and last thumbnails reach the centre.
        val sidePadding = (maxWidth - SLOT_WIDTH) / 2
        LazyRow(
            state = stripState,
            flingBehavior = rememberSnapFlingBehavior(stripState, SnapPosition.Center),
            contentPadding = PaddingValues(horizontal = sidePadding),
            horizontalArrangement = Arrangement.spacedBy(SLOT_GAP),
            modifier = Modifier.fillMaxWidth().testTag("film-strip"),
        ) {
            items(count = items.size, key = { items[it].id }) { index ->
                val isCurrent = index == pagerState.currentPage
                Box(
                    Modifier
                        .size(SLOT_WIDTH, SLOT_HEIGHT)
                        .then(if (isCurrent) Modifier.border(2.dp, Color.White).testTag("strip-current") else Modifier),
                ) {
                    Thumbnail(
                        items[index],
                        thumbnailPx,
                        Modifier.size(SLOT_WIDTH, SLOT_HEIGHT).clickable {
                            lead = Lead.TAP
                            scope.launch { pagerState.scrollToPage(index) }
                            scope.launch {
                                try {
                                    stripState.animateScrollToItem(index)
                                } finally {
                                    lead = Lead.PAGER
                                }
                            }
                        },
                    )
                }
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
