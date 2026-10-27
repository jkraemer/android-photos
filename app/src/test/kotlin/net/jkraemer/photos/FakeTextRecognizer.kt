package net.jkraemer.photos

import kotlinx.coroutines.delay
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.media.TextRecognizer

/**
 * Stands in for ML Kit: answers with [text] after [delayFor]
 * milliseconds, or throws when [fail] is set.
 */
class FakeTextRecognizer(
    private val text: (MediaItem) -> String = { "Text of ${it.id}" },
    private val delayFor: (MediaItem) -> Long = { 0 },
    private val fail: Boolean = false,
) : TextRecognizer {
    override suspend fun recognise(item: MediaItem): String {
        delay(delayFor(item))
        check(!fail) { "unreadable" }
        return text(item)
    }
}
