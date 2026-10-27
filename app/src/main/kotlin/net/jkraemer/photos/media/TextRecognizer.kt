package net.jkraemer.photos.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.cleanRecognisedText
import net.jkraemer.photos.core.scaledToLongEdge

/** Reads the text in a photo. */
interface TextRecognizer {
    /** The photo's text, empty when it has none; throws when the photo cannot be read. */
    suspend fun recognise(item: MediaItem): String
}

// Long enough for small print on a whole flyer, short enough to keep a run under a second.
private const val LONG_EDGE = 3000

/**
 * ML Kit's bundled Latin model, run on the phone. Its task cannot be
 * cancelled; a cancelled run (under a second) finishes and is ignored.
 */
class MlKitTextRecognizer(private val resolver: ContentResolver) : TextRecognizer {
    override suspend fun recognise(item: MediaItem): String = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(decode(item), 0)
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).use { client ->
            val text = suspendCancellableCoroutine { result ->
                client.process(image)
                    .addOnSuccessListener { result.resume(it.text) }
                    .addOnFailureListener { result.resumeWithException(it) }
            }
            cleanRecognisedText(text)
        }
    }

    /** The photo upright (ImageDecoder applies EXIF orientation), scaled down, in the format ML Kit prefers. */
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
