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

    @Test fun `opening the panel asks for the location once, unless granted or denied before`() {
        assertEquals(LocationAsk.REQUEST, LocationAsk.onPanelOpen(granted = false, deniedBefore = false, askedThisRun = false))
        assertEquals(LocationAsk.NOTHING, LocationAsk.onPanelOpen(granted = false, deniedBefore = false, askedThisRun = true))
        assertEquals(LocationAsk.NOTHING, LocationAsk.onPanelOpen(granted = false, deniedBefore = true, askedThisRun = false))
        assertEquals(LocationAsk.NOTHING, LocationAsk.onPanelOpen(granted = true, deniedBefore = false, askedThisRun = false))
    }

    @Test fun `Show location asks while Android still prompts, then sends to settings`() {
        assertEquals(LocationAsk.REQUEST, LocationAsk.onShowLocation(granted = false, canRequest = true))
        assertEquals(LocationAsk.SETTINGS, LocationAsk.onShowLocation(granted = false, canRequest = false))
        assertEquals(LocationAsk.NOTHING, LocationAsk.onShowLocation(granted = true, canRequest = true))
        assertEquals(LocationAsk.NOTHING, LocationAsk.onShowLocation(granted = true, canRequest = false))
    }
}
