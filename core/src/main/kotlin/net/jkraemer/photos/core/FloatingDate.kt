package net.jkraemer.photos.core

/** A grid cell currently laid out, with its vertical bounds in viewport pixels. */
data class VisibleCell(val itemIndex: Int, val top: Int, val bottom: Int)

/**
 * The newest item whose cell lies entirely inside the viewport. The floating
 * date names this item, because newest is at the bottom of the grid.
 */
fun newestFullyVisible(cells: List<VisibleCell>, viewportTop: Int, viewportBottom: Int): Int? =
    cells.filter { it.top >= viewportTop && it.bottom <= viewportBottom }.maxOfOrNull { it.itemIndex }
