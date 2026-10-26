package net.jkraemer.photos.core

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateLabelsTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val at = Instant.parse("2026-10-22T12:03:00Z")
    private val uk = DateLabels(Locale.UK, berlin)
    private val de = DateLabels(Locale.GERMANY, berlin)

    @Test fun `LARGE floats the day`() = assertEquals("22 Oct 2026", uk.floating(ZoomLevel.LARGE, at))

    @Test fun `MEDIUM floats the month`() {
        assertEquals("October 2026", uk.floating(ZoomLevel.MEDIUM, at))
        assertEquals("Oktober 2026", de.floating(ZoomLevel.MEDIUM, at))
    }

    @Test fun `header levels do not float`() {
        assertNull(uk.floating(ZoomLevel.SMALL, at))
        assertNull(uk.floating(ZoomLevel.TINY, at))
    }

    @Test fun `SMALL headers name the month short and its year, TINY the year`() {
        assertEquals(Headline("Oct", "2026"), uk.header(ZoomLevel.SMALL, at))
        assertEquals(Headline("Okt", "2026"), de.header(ZoomLevel.SMALL, at))
        assertEquals(Headline("2026", year = null), uk.header(ZoomLevel.TINY, at))
        assertNull(uk.header(ZoomLevel.LARGE, at))
    }

    @Test fun `timestamp is local date and time`() = assertEquals("22 Oct 2026, 14:03", uk.timestamp(at))
}
