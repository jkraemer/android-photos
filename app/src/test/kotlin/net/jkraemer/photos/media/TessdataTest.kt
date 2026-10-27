package net.jkraemer.photos.media

import java.io.File
import java.security.MessageDigest
import kotlin.concurrent.thread
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

    @Test fun `two installs at once both succeed and leave intact models`() {
        // Two activities' recognisers (before and after a rotation) on the first run.
        repeat(3) {
            tmp.root.listFiles()!!.forEach { it.deleteRecursively() }
            val failures = mutableListOf<Throwable>()
            List(2) {
                thread { runCatching { Tessdata(assets, tmp.root, appUpdatedAt = 0).install() }.onFailure { synchronized(failures) { failures += it } } }
            }.forEach { it.join() }
            assertEquals(emptyList<Throwable>(), failures)
            assertEquals("8407331d6aa0229dc927685c01a7938fc5a641d1a9524f74838cdac599f0d06e", sha256(model("deu")))
            assertEquals("8280aed0782fe27257a68ea10fe7ef324ca0f8d85bd2fd145d1c2b560bcb66ba", sha256(model("eng")))
        }
    }

    private fun sha256(file: File) =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    @Test fun `replaces a model older than the app`() {
        Tessdata(assets, tmp.root, appUpdatedAt = 0).install()
        model("deu").setLastModified(1_000)
        Tessdata(assets, tmp.root, appUpdatedAt = 2_000).install()
        assertTrue(model("deu").lastModified() > 2_000)
    }
}
