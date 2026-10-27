package net.jkraemer.photos.core

enum class LinkKind { PHONE, EMAIL, URL }

/** A stretch of recognised text that opens something; [target] is the URI to open. */
data class TextLink(val range: IntRange, val kind: LinkKind, val target: String)

private val URL = Regex("""(?:https?://|www\.)\S+""")
private val EMAIL = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")

// Runs of digit groups (digits, dashes, brackets) joined by single spaces or
// slashes. No '.', ':' or ',', which keeps times and prices out; a spaced
// dash or a double space ends the run, as between "0761 123456" and "10 - 18".
private val PHONE_RUN = Regex("""\+?\(?\d[\d()-]*(?:(?: ?/ ?| )\(?\d[\d()-]*)*""")
private val PHONE_GROUP = Regex("""[^ /]+""")

// Dates and time ranges that start with a zero like a phone number does.
private val DATE_OR_TIME_RANGE = Regex("""\b\d{1,2}([/.-])\d{1,2}\1\d{2,4}\b|\b\d{4} ?- ?\d{4}\b""")
private val ZERO_AFTER_COUNTRY_CODE = Regex("""^(\+\d{1,3})\s*\(0\)""")
private const val MIN_PHONE_DIGITS = 7
private const val MAX_PHONE_DIGITS = 15 // E.164's longest number

// A number is complete once it has this many digits; a further group on the
// same line is something else ("09957429363 6:30 AM").
private const val COMPLETE_DIGITS = 10
private const val COMPLETE_DIGITS_INTERNATIONAL = 11

/**
 * The phone numbers, email addresses and URLs in OCR output, in reading
 * order. A phone number has to start with `+` or `0` (a country or trunk
 * prefix, also in brackets), which rules out years, postcodes and amounts.
 */
fun findLinks(text: String): List<TextLink> {
    val links = mutableListOf<TextLink>()
    val dates = DATE_OR_TIME_RANGE.findAll(text).map { it.range }.toList()
    fun IntRange.overlaps(other: IntRange) = first <= other.last && other.first <= last
    fun free(range: IntRange) = links.none { it.range.overlaps(range) } && dates.none { it.overlaps(range) }

    for (match in URL.findAll(text)) {
        val url = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')')
        val target = if (url.startsWith("www.")) "https://$url" else url
        links += TextLink(match.range.first until match.range.first + url.length, LinkKind.URL, target)
    }
    for (match in EMAIL.findAll(text)) {
        if (free(match.range)) links += TextLink(match.range, LinkKind.EMAIL, "mailto:${match.value}")
    }
    for (run in PHONE_RUN.findAll(text)) {
        val groups = PHONE_GROUP.findAll(run.value)
            .map { run.range.first + it.range.first..run.range.first + it.range.last }
            .toList()
        var start = 0
        while (start < groups.size) {
            val (range, end) = phoneNumber(text, groups, start) ?: (null to start)
            if (range != null && free(range)) {
                val dialled = ZERO_AFTER_COUNTRY_CODE.replace(text.substring(range), "$1")
                val plus = if (dialled.startsWith("+")) "+" else ""
                links += TextLink(range, LinkKind.PHONE, "tel:$plus${dialled.filter { it.isDigit() }}")
            }
            start = end + 1
        }
    }
    return links.sortedBy { it.range.first }
}

/**
 * The number that starts at group [start], with the index of its last group,
 * or null when none starts there. It takes groups until it is complete, so
 * "0761/123456" is one number and "(035)5272818/09422058997" two. A leading
 * bracket that never closes is dropped.
 */
private fun phoneNumber(text: String, groups: List<IntRange>, start: Int): Pair<IntRange, Int>? {
    val first = text.substring(groups[start])
    if (!(first.startsWith("+") || first.startsWith("0") || first.startsWith("(0"))) return null
    val complete = if (first.startsWith("+")) COMPLETE_DIGITS_INTERNATIONAL else COMPLETE_DIGITS
    fun digits(end: Int) = ZERO_AFTER_COUNTRY_CODE.replace(text.substring(groups[start].first..groups[end].last), "$1")
        .count { it.isDigit() }
    var end = start
    while (end + 1 < groups.size && digits(end) < complete) end++
    if (digits(end) !in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS) return null
    var from = groups[start].first
    val to = groups[end].last
    if (text[from] == '(' && ')' !in text.substring(from, to + 1)) from++
    return (from..to) to end
}

/** OCR output without trailing spaces, with at most one blank line in a row, trimmed. */
fun cleanRecognisedText(raw: String): String =
    raw.lines().joinToString("\n") { it.trimEnd() }.replace(Regex("\n{3,}"), "\n\n").trim()

/** [width] × [height] shrunk so the longer edge is at most [longEdge], keeping the aspect ratio. */
fun scaledToLongEdge(width: Int, height: Int, longEdge: Int): Pair<Int, Int> {
    val scale = minOf(1.0, longEdge.toDouble() / maxOf(width, height))
    return Math.round(width * scale).toInt() to Math.round(height * scale).toInt()
}
