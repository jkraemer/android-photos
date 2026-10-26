package net.jkraemer.photos.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FilmStripTest {
    @Test fun `a settled page puts its item in the centre`() =
        assertEquals(StripScroll(10, 0), stripScrollFor(page = 10, pageOffsetFraction = 0f, pitchPx = 40f))

    @Test fun `half way to the newer page the strip is half a slot further`() =
        assertEquals(StripScroll(10, 20), stripScrollFor(page = 10, pageOffsetFraction = 0.5f, pitchPx = 40f))

    @Test fun `half way to the older page the strip is half a slot back`() =
        assertEquals(StripScroll(9, 20), stripScrollFor(page = 10, pageOffsetFraction = -0.5f, pitchPx = 40f))

    @Test fun `the centred item is the one whose slot covers the centre`() {
        assertEquals(10, centredItem(firstIndex = 10, firstOffsetPx = 19, pitchPx = 40f, count = 100))
        assertEquals(11, centredItem(firstIndex = 10, firstOffsetPx = 21, pitchPx = 40f, count = 100))
    }

    @Test fun `the centred item stays inside the timeline`() =
        assertEquals(99, centredItem(firstIndex = 99, firstOffsetPx = 30, pitchPx = 40f, count = 100))
}
