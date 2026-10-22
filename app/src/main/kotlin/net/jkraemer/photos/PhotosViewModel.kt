package net.jkraemer.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.ZoomLevel
import net.jkraemer.photos.media.MediaRepository

/**
 * Where the grid should be: at [itemId], or at the very bottom when
 * [pinnedToBottom] (the launch position and "following new photos").
 */
data class GridAnchor(val itemId: Long?, val pinnedToBottom: Boolean)

/** Screen state that has to survive rotation. */
class PhotosViewModel(repository: MediaRepository) : ViewModel() {
    /** The camera roll in timeline order; null until the first query returns. */
    val timeline: StateFlow<List<MediaItem>?> =
        repository.timeline().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _zoomLevel = MutableStateFlow(ZoomLevel.LARGE)
    val zoomLevel: StateFlow<ZoomLevel> = _zoomLevel.asStateFlow()

    private val _openItemId = MutableStateFlow<Long?>(null)
    val openItemId: StateFlow<Long?> = _openItemId.asStateFlow()

    private val _gridAnchor = MutableStateFlow(GridAnchor(itemId = null, pinnedToBottom = true))
    val gridAnchor: StateFlow<GridAnchor> = _gridAnchor.asStateFlow()

    fun setZoomLevel(level: ZoomLevel) {
        _zoomLevel.value = level
    }

    fun open(id: Long) {
        _openItemId.value = id
    }

    /** Leaves the detail view; the grid then shows [lastViewedId]. */
    fun close(lastViewedId: Long?) {
        _openItemId.value = null
        val newest = timeline.value?.lastOrNull()?.id
        _gridAnchor.value = GridAnchor(lastViewedId, pinnedToBottom = lastViewedId == null || lastViewedId == newest)
    }

    /** The grid reports its scroll position here as the user scrolls. */
    fun anchorGrid(anchor: GridAnchor) {
        _gridAnchor.value = anchor
    }
}
