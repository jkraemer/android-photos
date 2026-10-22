package net.jkraemer.photos.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomLevelTest {
    @Test fun `portrait uses the level's columns`() = assertEquals(3, ZoomLevel.LARGE.columnCount(412f, 412f))

    @Test fun `landscape keeps thumbnail size`() {
        assertEquals(6, ZoomLevel.LARGE.columnCount(892f, 412f))
        assertEquals(65, ZoomLevel.TINY.columnCount(892f, 412f))
    }

    @Test fun `closer and farther step and clamp`() {
        assertEquals(ZoomLevel.LARGE, ZoomLevel.MEDIUM.closer())
        assertEquals(ZoomLevel.LARGE, ZoomLevel.LARGE.closer())
        assertEquals(ZoomLevel.TINY, ZoomLevel.SMALL.farther())
        assertEquals(ZoomLevel.TINY, ZoomLevel.TINY.farther())
    }
}
