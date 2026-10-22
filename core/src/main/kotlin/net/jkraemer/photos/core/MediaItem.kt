package net.jkraemer.photos.core

import java.time.Instant

enum class MediaKind { IMAGE, VIDEO }

/** One camera-roll entry; [durationMs] is 0 for images. */
data class MediaItem(
    val id: Long,
    val takenAt: Instant,
    val kind: MediaKind,
    val mimeType: String,
    val durationMs: Long,
)

/**
 * The moment an item was captured. Some videos and copied-in files have no
 * DATE_TAKEN (null or 0); those fall back to the file's modification time
 * rather than landing in 1970.
 */
fun captureInstant(dateTakenMs: Long?, dateModifiedSec: Long): Instant =
    if (dateTakenMs != null && dateTakenMs > 0) Instant.ofEpochMilli(dateTakenMs)
    else Instant.ofEpochSecond(dateModifiedSec)

/** Oldest first; the id breaks ties so the order is stable across queries. */
fun List<MediaItem>.inTimelineOrder(): List<MediaItem> =
    sortedWith(compareBy<MediaItem> { it.takenAt }.thenBy { it.id })
