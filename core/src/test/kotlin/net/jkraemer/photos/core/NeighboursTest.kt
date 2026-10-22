package net.jkraemer.photos.core

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NeighboursTest {
    private val t = Instant.parse("2026-10-22T12:00:00Z")
    private fun items(vararg ids: Long) = ids.map { item(it, t.plusSeconds(it)) }

    @Test fun `lands on the older neighbour`() =
        assertEquals(1L, neighbourAfterRemoval(items(1, 2, 3), 2, items(1, 3)))

    @Test fun `lands on the newer neighbour when the oldest goes`() =
        assertEquals(2L, neighbourAfterRemoval(items(1, 2, 3), 1, items(2, 3)))

    @Test fun `skips neighbours that vanished too`() =
        assertEquals(4L, neighbourAfterRemoval(items(1, 2, 3, 4), 3, items(4)))

    @Test fun `null when nothing is left`() =
        assertNull(neighbourAfterRemoval(items(1), 1, items()))
}
