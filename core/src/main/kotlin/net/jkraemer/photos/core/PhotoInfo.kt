package net.jkraemer.photos.core

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * A point from a file's own numbers, or null when they are not a place on
 * Earth: edited or corrupt files carry NaN (GPS written as 0/0) or 91°, and
 * the system geocoder throws on anything out of range.
 */
fun geoPointOrNull(latitude: Double, longitude: Double): GeoPoint? =
    if (latitude in -90.0..90.0 && longitude in -180.0..180.0) GeoPoint(latitude, longitude) else null

/** What a file says about itself; a field the file lacks is null. */
data class MediaMetadata(
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val make: String? = null,
    val model: String? = null,
    val lensModel: String? = null,
    val focalLengthMm: Double? = null,
    val focalLength35mm: Int? = null,
    val fNumber: Double? = null,
    val exposureTimeSeconds: Double? = null,
    val iso: Int? = null,
    val exposureBiasEv: Double? = null,
    val location: GeoPoint? = null,
    /** A Google Camera RAW cover JPEG whose DNG sibling exists. */
    val hasRawOriginal: Boolean = false,
)

/** The info panel's lines; a null line (or an empty exposure row) is left out. */
data class InfoText(
    val date: String,
    val fileName: String,
    val camera: String?,
    val badge: String,
    val lens: String?,
    val dimensions: String?,
    val exposure: List<String>,
    val location: GeoPoint?,
)

private const val SEPARATOR = " · "

/** Formats [MediaMetadata] for the info panel, with the device [locale]'s decimal separator. */
class InfoFormat(private val locale: Locale) {
    private val symbols = DecimalFormatSymbols.getInstance(locale)
    private val upToOneDecimal = DecimalFormat("0.#", symbols)
    private val upToTwoDecimals = DecimalFormat("0.##", symbols)
    private val oneDecimal = DecimalFormat("0.0", symbols)
    private val signedUpToOneDecimal = DecimalFormat("+0.#;-0.#", symbols)

    fun text(meta: MediaMetadata, takenAt: Instant, labels: DateLabels): InfoText = InfoText(
        date = labels.infoDate(takenAt),
        fileName = meta.displayName,
        camera = cameraName(meta.make, meta.model),
        badge = badge(meta),
        lens = lensLine(meta),
        dimensions = dimensionsLine(meta),
        exposure = exposureRow(meta),
        location = meta.location,
    )

    /** Make and model, without repeating the make when the model already starts with it. */
    fun cameraName(make: String?, model: String?): String? = when {
        model == null -> make
        make == null || model.startsWith(make, ignoreCase = true) -> model
        else -> "$make $model"
    }

    /** "Back camera — 24 mm ƒ1.68": the lens name, then focal length and aperture. Photos only. */
    fun lensLine(meta: MediaMetadata): String? {
        if (!meta.isImage) return null
        val name = meta.lensModel?.let { lensName(it, meta.model) }
        val optics = listOfNotNull(focalLength(meta), meta.fNumber?.let(::aperture)).joinToString(" ").ifEmpty { null }
        return listOfNotNull(name, optics).joinToString(" — ").ifEmpty { null }
    }

    /** Photos: megapixels · pixels · size. Videos: pixels · duration · size. */
    fun dimensionsLine(meta: MediaMetadata): String? {
        val pixels = if (meta.width != null && meta.height != null) "${meta.width} × ${meta.height}" else null
        val size = meta.sizeBytes?.let(::fileSize)
        val parts = if (meta.isImage) {
            val mp = if (meta.width != null && meta.height != null) megapixels(meta.width, meta.height) else null
            listOfNotNull(mp, pixels, size)
        } else {
            listOfNotNull(pixels, meta.durationMs?.let(::formatDuration), size)
        }
        return parts.joinToString(SEPARATOR).ifEmpty { null }
    }

    /** ISO · focal length · exposure compensation · aperture · shutter. Photos only. */
    fun exposureRow(meta: MediaMetadata): List<String> {
        if (!meta.isImage) return emptyList()
        return listOfNotNull(
            meta.iso?.let { "ISO $it" },
            focalLength(meta),
            meta.exposureBiasEv?.let(::exposureBias),
            meta.fNumber?.let(::aperture),
            meta.exposureTimeSeconds?.let(::shutter),
        )
    }

    fun badge(meta: MediaMetadata): String = when {
        meta.hasRawOriginal -> "RAW"
        meta.mimeType == "image/heic" || meta.mimeType == "image/heif" -> "HEIF"
        else -> meta.mimeType.substringAfter('/').uppercase(Locale.ROOT)
    }

    fun aperture(f: Double): String = "ƒ" + upToTwoDecimals.format(f)

    /** Below a second as a fraction ("1/191 s"), otherwise in seconds. */
    fun shutter(seconds: Double): String =
        if (seconds < 1) "1/${(1 / seconds).roundToLong()} s" else "${upToOneDecimal.format(seconds)} s"

    fun exposureBias(ev: Double): String =
        if (ev == 0.0) "0 ev" else "${signedUpToOneDecimal.format(ev)} ev"

    /** Decimal units, as the system's storage settings count them. */
    fun fileSize(bytes: Long): String = when {
        bytes < 1_000_000 -> "${(bytes / 1_000.0).roundToLong()} KB"
        bytes < 1_000_000_000 -> "${oneDecimal.format(bytes / 1_000_000.0)} MB"
        else -> "${oneDecimal.format(bytes / 1_000_000_000.0)} GB"
    }

    fun megapixels(width: Int, height: Int): String =
        "${upToOneDecimal.format(width.toLong() * height / 1_000_000.0)} MP"

    /** The 35 mm equivalent, which is what photographers compare; the real one if that is missing. */
    private fun focalLength(meta: MediaMetadata): String? =
        meta.focalLength35mm?.let { "$it mm" } ?: meta.focalLengthMm?.let { "${upToOneDecimal.format(it)} mm" }

    /** A Pixel writes "Pixel 10 Pro back camera 6.9mm f/1.68"; the name is "Back camera". */
    private fun lensName(lensModel: String, model: String?): String? {
        val withoutModel = model?.trim()?.let { lensModel.removePrefix(it) } ?: lensModel
        return withoutModel.replace(TRAILING_OPTICS, "").trim()
            .replaceFirstChar { it.titlecase(locale) }
            .ifEmpty { null }
    }

    private val MediaMetadata.isImage get() = mimeType.startsWith("image/")

    private companion object {
        val TRAILING_OPTICS = Regex("""\s*[\d.]+\s*mm\s+f/[\d.]+$""")
    }
}

fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/** "54.5021° N, 11.2312° E", the same in every locale. */
fun coordinates(point: GeoPoint): String {
    val ns = if (point.latitude < 0) "S" else "N"
    val ew = if (point.longitude < 0) "W" else "E"
    return "%.4f° %s, %.4f° %s".format(Locale.ROOT, abs(point.latitude), ns, abs(point.longitude), ew)
}

/** A `geo:` link that centres a maps app on [point] and drops a pin there. */
fun geoUri(point: GeoPoint): String {
    val at = "${plain(point.latitude)},${plain(point.longitude)}"
    return "geo:$at?q=$at"
}

/**
 * The town (or failing that, the district or region) and its country. A
 * geocoder's feature name is often a house number on land, so it is only
 * used where there is no town, as at sea ("Fehmarn Belt").
 */
fun placeName(feature: String?, locality: String?, subAdminArea: String?, adminArea: String?, country: String?): String? {
    val area = locality ?: subAdminArea ?: adminArea
    return if (area != null) listOfNotNull(area, country).joinToString(", ") else feature ?: country
}

private fun plain(value: Double) = value.toBigDecimal().toPlainString()

private val ISO_6709 = Regex("""^([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""")

/** Parses a video's location ("+54.5021+011.2312/", altitude optional). */
fun parseIso6709(value: String): GeoPoint? {
    val match = ISO_6709.find(value) ?: return null
    val (lat, lng) = match.destructured
    return geoPointOrNull(lat.toDouble(), lng.toDouble())
}

/** Google Camera stores a RAW shot as `….RAW-01.COVER.jpg` next to `….RAW-02.ORIGINAL.dng`. */
fun rawOriginalName(displayName: String): String? =
    if (displayName.endsWith(RAW_COVER)) displayName.removeSuffix(RAW_COVER) + RAW_ORIGINAL else null

private const val RAW_COVER = ".RAW-01.COVER.jpg"
private const val RAW_ORIGINAL = ".RAW-02.ORIGINAL.dng"

/**
 * Whether the info panel ends up open after a vertical drag of [dragPx]
 * released at [velocityPx] (negative is upwards). A fast flick goes where it
 * points; a slow release goes by distance and otherwise stays where it was.
 */
fun panelOpenAfterDrag(open: Boolean, dragPx: Float, velocityPx: Float, thresholdPx: Float, velocityThresholdPx: Float): Boolean =
    when {
        velocityPx < -velocityThresholdPx -> true
        velocityPx > velocityThresholdPx -> false
        open -> dragPx <= thresholdPx
        else -> -dragPx > thresholdPx
    }
