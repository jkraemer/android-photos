package net.jkraemer.photos.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaAccessTest {
    @Test fun `access states`() {
        assertEquals(MediaAccess.FULL, MediaAccess.of(true, true, false))
        assertEquals(MediaAccess.FULL, MediaAccess.of(true, true, true))
        assertEquals(MediaAccess.PARTIAL, MediaAccess.of(false, false, true))
        assertEquals(MediaAccess.PARTIAL, MediaAccess.of(true, false, false))
        assertEquals(MediaAccess.NONE, MediaAccess.of(false, false, false))
    }
}
