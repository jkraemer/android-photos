package net.jkraemer.photos.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Every date string the app shows, formatted for the device [locale] in [zone]. */
class DateLabels(locale: Locale, zone: ZoneId) {
    private val day = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(zone)
    // "LLLL" is the standalone month name, which is what a label on its own needs
    // (e.g. Polish "październik" rather than the genitive "października").
    private val month = DateTimeFormatter.ofPattern("LLLL yyyy", locale).withZone(zone)
    private val year = DateTimeFormatter.ofPattern("yyyy", locale).withZone(zone)
    private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale).withZone(zone)

    /** The label floating over the grid, or null at levels that use headers instead. */
    fun floating(level: ZoomLevel, at: Instant): String? = when (level.dateContext) {
        DateContext.FLOATING_DAY -> day.format(at)
        DateContext.FLOATING_MONTH -> month.format(at)
        DateContext.MONTH_HEADERS, DateContext.YEAR_HEADERS -> null
    }

    /** The text of a header row, or null at levels without headers. */
    fun header(level: ZoomLevel, at: Instant): String? = when (level.dateContext) {
        DateContext.MONTH_HEADERS -> month.format(at)
        DateContext.YEAR_HEADERS -> year.format(at)
        DateContext.FLOATING_DAY, DateContext.FLOATING_MONTH -> null
    }

    /** Capture date and time for the detail view's top bar. */
    fun timestamp(at: Instant): String = dateTime.format(at)
}
