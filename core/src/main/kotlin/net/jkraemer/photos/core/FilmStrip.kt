package net.jkraemer.photos.core

import kotlin.math.floor
import kotlin.math.roundToInt

/** A film strip scroll position: the item at the centre slot's left edge, and how far past it. */
data class StripScroll(val index: Int, val offsetPx: Int)

/**
 * Where the strip scrolls while the pager sits [pageOffsetFraction] of a
 * page past [page]: the same fraction of a slot ([pitchPx]) past that item,
 * so the strip moves with the finger instead of jumping once the pager settles.
 */
fun stripScrollFor(page: Int, pageOffsetFraction: Float, pitchPx: Float): StripScroll {
    val position = page + pageOffsetFraction
    val index = floor(position).toInt()
    return StripScroll(index, ((position - index) * pitchPx).roundToInt())
}

/**
 * The item in the centre slot when the strip is scrolled [firstOffsetPx]
 * past [firstIndex]: the next one once more than half of [firstIndex]'s
 * slot has passed the centre.
 */
fun centredItem(firstIndex: Int, firstOffsetPx: Int, pitchPx: Float, count: Int): Int =
    (firstIndex + (firstOffsetPx / pitchPx).roundToInt()).coerceIn(0, count - 1)
