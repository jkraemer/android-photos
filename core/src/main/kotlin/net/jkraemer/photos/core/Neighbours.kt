package net.jkraemer.photos.core

/**
 * Where the detail view lands after [removedId] disappears from the timeline
 * (trashed here, or deleted by another app such as Immich): the nearest older
 * item from [before] that is still in [after], else the nearest newer one,
 * else null when nothing is left.
 */
fun neighbourAfterRemoval(before: List<MediaItem>, removedId: Long, after: List<MediaItem>): Long? {
    val removedIndex = before.indexOfFirst { it.id == removedId }
    if (removedIndex < 0) return null
    val remaining = after.mapTo(HashSet()) { it.id }
    val older = (removedIndex - 1 downTo 0).asSequence().map { before[it].id }
    val newer = (removedIndex + 1 until before.size).asSequence().map { before[it].id }
    return (older + newer).firstOrNull { it in remaining }
}
