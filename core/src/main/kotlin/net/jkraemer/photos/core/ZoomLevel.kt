package net.jkraemer.photos.core

import kotlin.math.max
import kotlin.math.roundToInt

/** How a zoom level tells the viewer where in time they are. */
enum class DateContext { FLOATING_DAY, FLOATING_MONTH, MONTH_HEADERS, YEAR_HEADERS }

/** The four grid densities, from closest (largest thumbnails) to farthest. */
enum class ZoomLevel(
    val portraitColumns: Int,
    val dateContext: DateContext,
    val gapDp: Int,
    val showsVideoDuration: Boolean,
) {
    LARGE(3, DateContext.FLOATING_DAY, 2, true),
    MEDIUM(5, DateContext.FLOATING_MONTH, 2, true),
    SMALL(15, DateContext.MONTH_HEADERS, 1, false),
    TINY(30, DateContext.YEAR_HEADERS, 0, false);

    /** One level fewer columns, clamped at [LARGE]. */
    fun closer(): ZoomLevel = entries[max(ordinal - 1, 0)]

    /** One level more columns, clamped at [TINY]. */
    fun farther(): ZoomLevel = entries[minOf(ordinal + 1, entries.lastIndex)]

    /**
     * Columns for a viewport [widthDp] wide. Scaling by the screen's shortest
     * side keeps the portrait thumbnail size in landscape, which therefore
     * gets proportionally more columns.
     */
    fun columnCount(widthDp: Float, shortestSideDp: Float): Int =
        max(portraitColumns, (portraitColumns * widthDp / shortestSideDp).roundToInt())
}
