package net.jkraemer.photos.core

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaItemTest {
    @Test fun `uses DATE_TAKEN when present`() =
        assertEquals(Instant.ofEpochMilli(1_792_000_000_123), captureInstant(1_792_000_000_123, 1_000))

    @Test fun `falls back to DATE_MODIFIED when DATE_TAKEN is null`() =
        assertEquals(Instant.ofEpochSecond(1_792_000_000), captureInstant(null, 1_792_000_000))

    @Test fun `falls back to DATE_MODIFIED when DATE_TAKEN is zero`() =
        assertEquals(Instant.ofEpochSecond(1_792_000_000), captureInstant(0, 1_792_000_000))

    @Test fun `timeline is oldest first with ties broken by id`() {
        val t = Instant.parse("2026-10-22T12:00:00Z")
        val items = listOf(item(id = 3, t), item(id = 1, t.plusSeconds(60)), item(id = 2, t))
        assertEquals(listOf(2L, 3L, 1L), items.inTimelineOrder().map { it.id })
    }
}
