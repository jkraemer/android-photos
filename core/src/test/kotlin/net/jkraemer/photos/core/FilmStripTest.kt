package net.jkraemer.photos.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FilmStripTest {
    @Test fun `window centres current`() = assertEquals((0..10).toList(), filmStripWindow(5, 100))

    @Test fun `window pads at the oldest end`() =
        assertEquals(listOf(null, null, null, null, null, 0, 1, 2, 3, 4, 5), filmStripWindow(0, 100))

    @Test fun `window pads at the newest end`() =
        assertEquals(listOf(94, 95, 96, 97, 98, 99, null, null, null, null, null), filmStripWindow(99, 100))

    @Test fun `window with a single item`() =
        assertEquals(List(5) { null } + 0 + List(5) { null }, filmStripWindow(0, 1))

    @Test fun `dragging left two slots moves two newer`() = assertEquals(12, scrubTarget(10, -2 * 40f, 40f, 100))

    @Test fun `scrub clamps to the timeline`() {
        assertEquals(0, scrubTarget(1, 400f, 40f, 100))
        assertEquals(99, scrubTarget(98, -400f, 40f, 100))
    }
}
