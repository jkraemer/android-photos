package net.jkraemer.photos.core

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoInfoTest {
    private val uk = InfoFormat(Locale.UK)
    private val de = InfoFormat(Locale.GERMANY)
    private val labels = DateLabels(Locale.UK, ZoneId.of("Europe/Berlin"))

    /** The values a Pixel 10 Pro wrote into one of its JPEGs. */
    private val pixel = MediaMetadata(
        "PXL_20261025_025836525.jpg", "image/jpeg",
        sizeBytes = 2_959_304, width = 4080, height = 3072,
        make = "Google", model = "Pixel 10 Pro", lensModel = "Pixel 10 Pro back camera 6.9mm f/1.68",
        focalLengthMm = 6.9, focalLength35mm = 24, fNumber = 1.68, exposureTimeSeconds = 82.0 / 15625,
        iso = 51, exposureBiasEv = 0.0, location = GeoPoint(54.5021, 11.2312),
    )

    @Test fun `camera name joins make and model unless the model repeats the make`() {
        assertEquals("Google Pixel 10 Pro", uk.cameraName("Google", "Pixel 10 Pro"))
        assertEquals("Google Pixel 3", uk.cameraName("Google", "Google Pixel 3"))
        assertEquals("Pixel 10 Pro", uk.cameraName(null, "Pixel 10 Pro"))
        assertNull(uk.cameraName(null, null))
    }

    @Test fun `lens line names the lens, 35mm focal length and aperture`() {
        assertEquals("Back camera — 24 mm ƒ1.68", uk.lensLine(pixel))
        assertEquals("Back camera — 24 mm ƒ1,68", de.lensLine(pixel))
        assertEquals("24 mm ƒ1.68", uk.lensLine(pixel.copy(lensModel = null)))
        assertEquals("Back camera — 6.9 mm ƒ1.68", uk.lensLine(pixel.copy(focalLength35mm = null)))
        assertNull(uk.lensLine(pixel.copy(lensModel = null, focalLengthMm = null, focalLength35mm = null, fNumber = null)))
    }

    @Test fun `photo dimensions are megapixels, pixels and size`() =
        assertEquals("12.5 MP · 4080 × 3072 · 3.0 MB", uk.dimensionsLine(pixel))

    @Test fun `video dimensions are pixels, duration and size`() = assertEquals(
        "1920 × 1080 · 0:26 · 45.2 MB",
        uk.dimensionsLine(MediaMetadata("PXL_1.mp4", "video/mp4", sizeBytes = 45_200_000, width = 1920, height = 1080, durationMs = 26_000)),
    )

    @Test fun `file sizes`() {
        assertEquals("850 KB", uk.fileSize(850_000))
        assertEquals("3.0 MB", uk.fileSize(2_959_304))
        assertEquals("1.2 GB", uk.fileSize(1_200_000_000))
    }

    @Test fun `megapixels drop a trailing zero`() {
        assertEquals("50 MP", uk.megapixels(8000, 6250))
        assertEquals("12.5 MP", uk.megapixels(4080, 3072))
    }

    @Test fun `exposure row`() =
        assertEquals(listOf("ISO 51", "24 mm", "0 ev", "ƒ1.68", "1/191 s"), uk.exposureRow(pixel))

    @Test fun `shutter speeds`() {
        assertEquals("1/191 s", uk.shutter(82.0 / 15625))
        assertEquals("1/2 s", uk.shutter(0.5))
        assertEquals("2 s", uk.shutter(2.0))
        assertEquals("1.5 s", uk.shutter(1.5))
    }

    @Test fun `exposure bias is signed`() {
        assertEquals("0 ev", uk.exposureBias(0.0))
        assertEquals("+0.7 ev", uk.exposureBias(0.7))
        assertEquals("-1 ev", uk.exposureBias(-1.0))
    }

    @Test fun `exposure row leaves out what is missing`() =
        assertEquals(listOf("ISO 51"), uk.exposureRow(MediaMetadata("a.jpg", "image/jpeg", iso = 51)))

    @Test fun `videos have no exposure row`() =
        assertEquals(emptyList<String>(), uk.exposureRow(pixel.copy(mimeType = "video/mp4")))

    @Test fun `badges`() {
        assertEquals("JPEG", uk.badge(pixel))
        assertEquals("RAW", uk.badge(pixel.copy(hasRawOriginal = true)))
        assertEquals("HEIF", uk.badge(pixel.copy(mimeType = "image/heic")))
        assertEquals("MP4", uk.badge(pixel.copy(mimeType = "video/mp4")))
        assertEquals("PNG", uk.badge(pixel.copy(mimeType = "image/png")))
    }

    @Test fun `all-empty metadata keeps date, file and badge`() = assertEquals(
        InfoText("Thursday · 22 Oct 2026 · 10:00", "a.png", null, "PNG", null, null, emptyList(), null),
        uk.text(MediaMetadata("a.png", "image/png"), Instant.parse("2026-10-22T08:00:00Z"), labels),
    )

    @Test fun `a full photo fills every line`() = assertEquals(
        InfoText(
            "Thursday · 22 Oct 2026 · 10:00", "PXL_20261025_025836525.jpg", "Google Pixel 10 Pro", "JPEG",
            "Back camera — 24 mm ƒ1.68", "12.5 MP · 4080 × 3072 · 3.0 MB",
            listOf("ISO 51", "24 mm", "0 ev", "ƒ1.68", "1/191 s"), GeoPoint(54.5021, 11.2312),
        ),
        uk.text(pixel, Instant.parse("2026-10-22T08:00:00Z"), labels),
    )

    @Test fun `coordinates use hemispheres and four decimals`() {
        assertEquals("54.5021° N, 11.2312° E", coordinates(GeoPoint(54.50214, 11.23118)))
        assertEquals("33.8688° S, 151.2093° W", coordinates(GeoPoint(-33.8688, -151.2093)))
    }

    @Test fun `geo uri marks the point`() =
        assertEquals("geo:54.5021,11.2312?q=54.5021,11.2312", geoUri(GeoPoint(54.5021, 11.2312)))

    @Test fun `ISO 6709 locations from videos`() {
        assertEquals(GeoPoint(54.5021, 11.2312), parseIso6709("+54.5021+011.2312/"))
        assertEquals(GeoPoint(-33.8688, -151.2093), parseIso6709("-33.8688-151.2093+010.000/"))
        assertNull(parseIso6709("garbage"))
    }

    @Test fun `a RAW cover names its DNG sibling`() {
        assertEquals("PXL_20260713_164100123.RAW-02.ORIGINAL.dng", rawOriginalName("PXL_20260713_164100123.RAW-01.COVER.jpg"))
        assertNull(rawOriginalName("PXL_20260713_164100123.jpg"))
    }

    @Test fun `durations are minutes and seconds`() {
        assertEquals("0:26", formatDuration(26_000))
        assertEquals("12:05", formatDuration(725_000))
    }

    @Test fun `place names prefer the town, then the area, then the feature`() {
        assertEquals("Fehmarn Belt", placeName("Fehmarn Belt", null, null, null, null))
        assertEquals("Burg auf Fehmarn, Germany", placeName(null, "Burg auf Fehmarn", "Ostholstein", "Schleswig-Holstein", "Germany"))
        assertEquals("Ostholstein, Germany", placeName(null, null, "Ostholstein", "Schleswig-Holstein", "Germany"))
        assertEquals("Schleswig-Holstein, Germany", placeName(null, null, null, "Schleswig-Holstein", "Germany"))
        assertEquals("Burg auf Fehmarn", placeName(null, "Burg auf Fehmarn", null, null, null))
        assertEquals("Fehmarn Belt", placeName("Fehmarn Belt", null, null, null, "Germany"))
        assertEquals("Germany", placeName(null, null, null, null, "Germany"))
        assertNull(placeName(null, null, null, null, null))
    }

    private fun afterDrag(open: Boolean, dragPx: Float, velocityPx: Float = 0f) =
        panelOpenAfterDrag(open, dragPx, velocityPx, thresholdPx = 100f, velocityThresholdPx = 1000f)

    @Test fun `a long drag up opens the panel`() = assertEquals(true, afterDrag(open = false, dragPx = -150f))

    @Test fun `a short slow drag up leaves it closed`() = assertEquals(false, afterDrag(open = false, dragPx = -50f, velocityPx = -200f))

    @Test fun `a fast short flick up opens it`() = assertEquals(true, afterDrag(open = false, dragPx = -30f, velocityPx = -2000f))

    @Test fun `a long drag down closes an open panel`() = assertEquals(false, afterDrag(open = true, dragPx = 150f))

    @Test fun `a short drag down leaves it open`() = assertEquals(true, afterDrag(open = true, dragPx = 50f))

    @Test fun `a fast flick down closes it`() = assertEquals(false, afterDrag(open = true, dragPx = 20f, velocityPx = 2000f))

    @Test fun `flicking back up after a long drag down keeps it open`() =
        assertEquals(true, afterDrag(open = true, dragPx = 150f, velocityPx = -2000f))
}
