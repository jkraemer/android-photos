package net.jkraemer.photos.core

import java.time.ZoneId

/** One slot of the grid: a full-width period header or a thumbnail cell. */
sealed interface GridEntry {
    /** Precedes the first item of a month or year; its label comes from that item. */
    data class Header(val firstItemIndex: Int) : GridEntry
    data class Cell(val itemIndex: Int) : GridEntry
}

/**
 * The grid's entries for [items] (in timeline order) at [level]. Header
 * levels start each month or year with a [GridEntry.Header], judged in
 * [zone] so a photo taken just after local midnight lands in the new period.
 */
class TimelineGrid(items: List<MediaItem>, level: ZoomLevel, zone: ZoneId) {
    val entries: List<GridEntry>
    private val entryIndexByItem = IntArray(items.size)

    init {
        val periodOf: ((MediaItem) -> Int)? = when (level.dateContext) {
            DateContext.MONTH_HEADERS -> { item -> item.takenAt.atZone(zone).let { it.year * 12 + it.monthValue } }
            DateContext.YEAR_HEADERS -> { item -> item.takenAt.atZone(zone).year }
            DateContext.FLOATING_DAY, DateContext.FLOATING_MONTH -> null
        }
        val result = ArrayList<GridEntry>(items.size + 64)
        var previousPeriod: Int? = null
        items.forEachIndexed { index, item ->
            if (periodOf != null) {
                val period = periodOf(item)
                if (period != previousPeriod) result.add(GridEntry.Header(index))
                previousPeriod = period
            }
            entryIndexByItem[index] = result.size
            result.add(GridEntry.Cell(index))
        }
        entries = result
    }

    /** The entry position of timeline item [itemIndex]. */
    fun entryIndexOf(itemIndex: Int): Int = entryIndexByItem[itemIndex]
}
