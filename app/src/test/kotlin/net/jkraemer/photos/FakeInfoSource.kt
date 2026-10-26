package net.jkraemer.photos

import kotlinx.coroutines.delay
import net.jkraemer.photos.core.GeoPoint
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.MediaMetadata
import net.jkraemer.photos.ui.detail.InfoSource

/**
 * Stands in for MediaStore and the geocoder: answers with [metadata] after
 * [delayFor] milliseconds, and names every place [place].
 */
class FakeInfoSource(
    private val metadata: (MediaItem) -> MediaMetadata = { MediaMetadata("item-${it.id}.jpg", it.mimeType) },
    private val place: String? = null,
    private val delayFor: (MediaItem) -> Long = { 0 },
) : InfoSource {
    override suspend fun metadata(item: MediaItem, withLocation: Boolean): MediaMetadata {
        delay(delayFor(item))
        return metadata(item).let { if (withLocation) it else it.copy(location = null) }
    }

    override suspend fun placeName(point: GeoPoint): String? = place
}
