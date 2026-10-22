package net.jkraemer.photos

import java.time.Instant
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.MediaKind

fun item(id: Long, takenAt: Instant, kind: MediaKind = MediaKind.IMAGE, durationMs: Long = 0) =
    MediaItem(
        id = id,
        takenAt = takenAt,
        kind = kind,
        mimeType = if (kind == MediaKind.VIDEO) "video/mp4" else "image/jpeg",
        durationMs = durationMs,
    )
