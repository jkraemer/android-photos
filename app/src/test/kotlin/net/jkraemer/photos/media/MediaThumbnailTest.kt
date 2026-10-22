package net.jkraemer.photos.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaThumbnailTest {
    @Test fun `thumbnail size rounds up to a multiple of 64`() {
        assertEquals(64, thumbnailSizePx(1))
        assertEquals(384, thumbnailSizePx(384))
        assertEquals(448, thumbnailSizePx(385))
    }
}
