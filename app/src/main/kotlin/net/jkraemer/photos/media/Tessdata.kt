package net.jkraemer.photos.media

import android.content.res.AssetManager
import java.io.File

/**
 * Tesseract's language models. They ship as assets, but Tesseract reads
 * them only from files, so [install] copies them into [dir] — again when a
 * copy is incomplete or older than the app ([appUpdatedAt], ms), which may
 * have brought new ones.
 */
class Tessdata(private val assets: AssetManager, private val dir: File, private val appUpdatedAt: Long) {
    /** Makes sure every model is in `dir/tessdata/`; returns [dir], the path Tesseract's init wants. */
    fun install(): File {
        val target = File(dir, "tessdata").apply { mkdirs() }
        for (language in LANGUAGES.split('+')) {
            val name = "$language.traineddata"
            val file = File(target, name)
            val length = assets.openFd("tessdata/$name").use { it.length }
            if (file.length() != length || file.lastModified() < appUpdatedAt) {
                // Through a temporary file, so a copy cut short never passes for a model.
                val partial = File(target, "$name.partial")
                assets.open("tessdata/$name").use { input -> partial.outputStream().use { input.copyTo(it) } }
                check(partial.renameTo(file)) { "could not move $partial to $file" }
            }
        }
        return dir
    }

    companion object {
        const val LANGUAGES = "deu+eng"
    }
}
