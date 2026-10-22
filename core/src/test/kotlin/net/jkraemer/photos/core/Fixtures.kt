package net.jkraemer.photos.core

import java.time.Instant

fun item(id: Long, takenAt: Instant, kind: MediaKind = MediaKind.IMAGE, durationMs: Long = 0) =
    MediaItem(
        id = id,
        takenAt = takenAt,
        kind = kind,
        mimeType = if (kind == MediaKind.VIDEO) "video/mp4" else "image/jpeg",
        durationMs = durationMs,
    )
