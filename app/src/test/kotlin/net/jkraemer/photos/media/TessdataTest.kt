package net.jkraemer.photos.media

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Runs on the real models the build downloads into the app's assets. */
@RunWith(RobolectricTestRunner::class)
class TessdataTest {
    @get:Rule val tmp = TemporaryFolder()
    private val assets = RuntimeEnvironment.getApplication().assets
    private fun model(name: String) = File(tmp.root, "tessdata/$name.traineddata")

    @Test fun `copies both models on first install`() {
        val dir = Tessdata(assets, tmp.root, appUpdatedAt = 0).install()
        assertEquals(tmp.root, dir)
        assertEquals(8_628_461L, model("deu").length())
        assertEquals(15_400_601L, model("eng").length())
    }

    @Test fun `leaves an up to date model alone`() {
        Tessdata(assets, tmp.root, appUpdatedAt = 0).install()
        model("eng").setLastModified(5_000)
        Tessdata(assets, tmp.root, appUpdatedAt = 1_000).install()
        assertEquals(5_000L, model("eng").lastModified())
    }

    @Test fun `replaces a model of the wrong size`() {
        model("deu").apply { parentFile!!.mkdirs() }.writeText("broken")
        Tessdata(assets, tmp.root, appUpdatedAt = 0).install()
        assertEquals(8_628_461L, model("deu").length())
    }

    @Test fun `replaces a model older than the app`() {
        Tessdata(assets, tmp.root, appUpdatedAt = 0).install()
        model("deu").setLastModified(1_000)
        Tessdata(assets, tmp.root, appUpdatedAt = 2_000).install()
        assertTrue(model("deu").lastModified() > 2_000)
    }
}
