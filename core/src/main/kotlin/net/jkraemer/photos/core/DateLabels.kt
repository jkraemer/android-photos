package net.jkraemer.photos.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** A header's text: a month or year [name], and the [year] a month belongs to, shown subdued. */
data class Headline(val name: String, val year: String?)

/** Every date string the app shows, formatted for the device [locale] in [zone]. */
class DateLabels(val locale: Locale, val zone: ZoneId) {
    private val day = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(zone)
    // "LLLL" and "LLL" are the standalone month names, which is what a label on its own needs
    // (e.g. Polish "październik" rather than the genitive "października").
    private val month = DateTimeFormatter.ofPattern("LLLL yyyy", locale).withZone(zone)
    private val shortMonth = DateTimeFormatter.ofPattern("LLL", locale).withZone(zone)
    private val year = DateTimeFormatter.ofPattern("yyyy", locale).withZone(zone)
    private val weekday = DateTimeFormatter.ofPattern("EEEE", locale).withZone(zone)
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale).withZone(zone)

    /** The label floating over the grid, or null at levels that use headers instead. */
    fun floating(level: ZoomLevel, at: Instant): String? = when (level.dateContext) {
        DateContext.FLOATING_DAY -> day.format(at)
        DateContext.FLOATING_MONTH -> month.format(at)
        DateContext.MONTH_HEADERS, DateContext.YEAR_HEADERS -> null
    }

    /** The text of a header row, or null at levels without headers. */
    fun header(level: ZoomLevel, at: Instant): Headline? = when (level.dateContext) {
        DateContext.MONTH_HEADERS -> Headline(shortMonth.format(at), year.format(at))
        DateContext.YEAR_HEADERS -> Headline(year.format(at), null)
        DateContext.FLOATING_DAY, DateContext.FLOATING_MONTH -> null
    }

    /** Weekday, date and time for the info panel ("Thursday · 22 Oct 2026 · 10:00"). */
    fun infoDate(at: Instant): String = "${weekday.format(at)} · ${day.format(at)} · ${time.format(at)}"

    /** Capture date and time for the detail view's top bar. */
    fun timestamp(at: Instant): String = dateTime.format(at)
}
