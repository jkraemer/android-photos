package net.jkraemer.photos

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.media.MediaRepository

/** A camera roll whose contents the test sets directly. */
class FakeMediaRepository(initial: List<MediaItem>) : MediaRepository {
    private val items = MutableStateFlow(initial)

    override fun timeline(): Flow<List<MediaItem>> = items

    fun set(items: List<MediaItem>) {
        this.items.value = items
    }
}
