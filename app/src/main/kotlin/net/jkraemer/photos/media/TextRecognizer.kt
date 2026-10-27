package net.jkraemer.photos.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.cleanRecognisedText
import net.jkraemer.photos.core.scaledToLongEdge

/** Reads the text in a photo. */
interface TextRecognizer {
    /** The photo's text, empty when it has none; throws when the photo cannot be read. */
    suspend fun recognise(item: MediaItem): String
}

// Long enough for small print on a whole flyer, short enough to keep a run to seconds.
private const val LONG_EDGE = 3000

/** Tesseract on the German and English models; runs off the main thread and stops when cancelled. */
class TesseractTextRecognizer(private val resolver: ContentResolver, private val tessdata: Tessdata) : TextRecognizer {
    // Two runs at once must not copy the models over each other.
    private val installing = Mutex()

    override suspend fun recognise(item: MediaItem): String = withContext(Dispatchers.Default) {
        val dir = installing.withLock { tessdata.install() }
        val bitmap = decode(item)
        val api = TessBaseAPI()
        try {
            check(api.init(dir.path, Tessdata.LANGUAGES)) { "Tesseract could not load ${Tessdata.LANGUAGES} from $dir" }
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            api.setImage(bitmap)
            bitmap.recycle()
            // Returns only once the native run has, so recycle() never pulls the engine from under it.
            coroutineScope {
                // getHOCRText is the only call stop() interrupts; getUTF8Text then returns its result.
                val run = async { api.getHOCRText(0) }
                try {
                    run.await()
                } catch (e: CancellationException) {
                    api.stop()
                    throw e
                }
            }
            cleanRecognisedText(api.getUTF8Text().orEmpty())
        } finally {
            api.recycle()
        }
    }

    /** The photo upright (ImageDecoder applies EXIF orientation), scaled down, in the only format Tesseract takes. */
    private fun decode(item: MediaItem): Bitmap {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, contentUriOf(item))) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val (width, height) = scaledToLongEdge(info.size.width, info.size.height, LONG_EDGE)
            decoder.setTargetSize(width, height)
        }
        if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
        return bitmap.copy(Bitmap.Config.ARGB_8888, false).also { bitmap.recycle() }
    }
}
