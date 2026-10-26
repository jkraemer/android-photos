package net.jkraemer.photos

import java.time.Instant
import net.jkraemer.photos.core.GeoPoint
import net.jkraemer.photos.core.MediaItem
import net.jkraemer.photos.core.MediaKind
import net.jkraemer.photos.core.MediaMetadata

fun item(id: Long, takenAt: Instant, kind: MediaKind = MediaKind.IMAGE, durationMs: Long = 0) =
    MediaItem(
        id = id,
        takenAt = takenAt,
        kind = kind,
        mimeType = if (kind == MediaKind.VIDEO) "video/mp4" else "image/jpeg",
        durationMs = durationMs,
    )

/** The values a Pixel 10 Pro wrote into one of its JPEGs. */
val pixelMetadata = MediaMetadata(
    "PXL_20261025_025836525.jpg", "image/jpeg",
    sizeBytes = 2_959_304, width = 4080, height = 3072,
    make = "Google", model = "Pixel 10 Pro", lensModel = "Pixel 10 Pro back camera 6.9mm f/1.68",
    focalLengthMm = 6.9, focalLength35mm = 24, fNumber = 1.68, exposureTimeSeconds = 82.0 / 15625,
    iso = 51, exposureBiasEv = 0.0, location = GeoPoint(54.5021, 11.2312),
)
