package net.jkraemer.photos.core

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.system.measureTimeMillis
import net.jkraemer.photos.core.GridEntry.Cell
import net.jkraemer.photos.core.GridEntry.Header
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineGridTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val sepSepOct = listOf(
        item(1, Instant.parse("2026-09-10T12:00:00Z")),
        item(2, Instant.parse("2026-09-20T12:00:00Z")),
        item(3, Instant.parse("2026-10-05T12:00:00Z")),
    )

    @Test fun `LARGE and MEDIUM have no headers`() {
        val expected = listOf(Cell(0), Cell(1), Cell(2))
        assertEquals(expected, TimelineGrid(sepSepOct, ZoomLevel.LARGE, berlin).entries)
        assertEquals(expected, TimelineGrid(sepSepOct, ZoomLevel.MEDIUM, berlin).entries)
    }

    @Test fun `SMALL puts a header before each month`() =
        assertEquals(
            listOf(Header(0), Cell(0), Cell(1), Header(2), Cell(2)),
            TimelineGrid(sepSepOct, ZoomLevel.SMALL, berlin).entries,
        )

    @Test fun `TINY puts a header before each year`() {
        val items = listOf(
            item(1, Instant.parse("2025-12-10T12:00:00Z")),
            item(2, Instant.parse("2026-01-10T12:00:00Z")),
            item(3, Instant.parse("2026-02-10T12:00:00Z")),
        )
        assertEquals(
            listOf(Header(0), Cell(0), Header(1), Cell(1), Cell(2)),
            TimelineGrid(items, ZoomLevel.TINY, berlin).entries,
        )
    }

    @Test fun `month boundary follows the local zone`() {
        // 2026-10-31T23:30:00Z is 00:30 on 1 Nov in Berlin
        val items = listOf(
            item(1, Instant.parse("2026-10-30T12:00:00Z")),
            item(2, Instant.parse("2026-10-31T23:30:00Z")),
        )
        assertEquals(
            listOf(Header(0), Cell(0), Header(1), Cell(1)),
            TimelineGrid(items, ZoomLevel.SMALL, berlin).entries,
        )
    }

    @Test fun `entryIndexOf maps items past headers`() =
        assertEquals(4, TimelineGrid(sepSepOct, ZoomLevel.SMALL, berlin).entryIndexOf(2))

    @Test fun `empty timeline has no entries`() =
        assertEquals(emptyList<GridEntry>(), TimelineGrid(emptyList(), ZoomLevel.SMALL, berlin).entries)

    @Test fun `builds 50k items quickly`() {
        val start = Instant.parse("2025-01-01T00:00:00Z")
        val items = List(50_000) { item(it.toLong(), start.plus(Duration.ofMinutes(20L * it))) }
        val months = items.map { it.takenAt.atZone(berlin).let { z -> z.year * 12 + z.monthValue } }.distinct().size
        TimelineGrid(items, ZoomLevel.SMALL, berlin)
        lateinit var grid: TimelineGrid
        val elapsed = measureTimeMillis { grid = TimelineGrid(items, ZoomLevel.SMALL, berlin) }
        assertEquals(50_000 + months, grid.entries.size)
        assertEquals(grid.entries.lastIndex, grid.entryIndexOf(49_999))
        assertTrue("took $elapsed ms", elapsed < 500)
    }
}
