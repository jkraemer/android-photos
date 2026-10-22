package net.jkraemer.photos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloatingDateTest {
    @Test fun `picks the newest cell entirely inside the viewport`() {
        val cells = listOf(
            VisibleCell(10, -50, 80),
            VisibleCell(11, 0, 130),
            VisibleCell(12, 140, 270),
            VisibleCell(13, 900, 1030),
        )
        // 13 is cut off at the bottom
        assertEquals(12, newestFullyVisible(cells, viewportTop = 0, viewportBottom = 1000))
    }

    @Test fun `null when nothing is fully visible`() =
        assertNull(newestFullyVisible(listOf(VisibleCell(1, -10, 2000)), 0, 1000))
}
