package net.jkraemer.photos.core

enum class LinkKind { PHONE, EMAIL, URL }

/** A stretch of recognised text that opens something; [target] is the URI to open. */
data class TextLink(val range: IntRange, val kind: LinkKind, val target: String)

private val URL = Regex("""(?:https?://|www\.)\S+""")
private val EMAIL = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")

// Digits with the separators phone numbers are printed with. No '.', ':' or
// ',', which keeps dates, times and prices out.
private val PHONE_CANDIDATE = Regex("""\+?\(?\d[\d ()/-]*\d""")
private val ZERO_AFTER_COUNTRY_CODE = Regex("""^(\+\d{1,3})\s*\(0\)""")
private const val MIN_PHONE_DIGITS = 7

// E.164's longest number; more digits joined by slashes are several numbers.
private const val MAX_PHONE_DIGITS = 15

/**
 * The phone numbers, email addresses and URLs in OCR output, in reading
 * order. A phone number has to start with `+` or `0` (a country or trunk
 * prefix, also in brackets), which rules out years, postcodes and amounts.
 */
fun findLinks(text: String): List<TextLink> {
    val links = mutableListOf<TextLink>()
    fun free(range: IntRange) = links.none { it.range.first <= range.last && range.first <= it.range.last }

    for (match in URL.findAll(text)) {
        val url = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')')
        val target = if (url.startsWith("www.")) "https://$url" else url
        links += TextLink(match.range.first until match.range.first + url.length, LinkKind.URL, target)
    }
    for (match in EMAIL.findAll(text)) {
        if (free(match.range)) links += TextLink(match.range, LinkKind.EMAIL, "mailto:${match.value}")
    }
    for (match in PHONE_CANDIDATE.findAll(text)) {
        for (range in phoneParts(text, match.range)) {
            val number = text.substring(range)
            val dialled = ZERO_AFTER_COUNTRY_CODE.replace(number, "$1")
            val digits = dialled.count { it.isDigit() }
            val prefixed = number.startsWith("+") || number.startsWith("0") || number.startsWith("(0")
            if (prefixed && digits in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS && free(range)) {
                val plus = if (dialled.startsWith("+")) "+" else ""
                links += TextLink(range, LinkKind.PHONE, "tel:$plus${dialled.filter { it.isDigit() }}")
            }
        }
    }
    return links.sortedBy { it.range.first }
}

/**
 * The candidate as one number, or split at its slashes when its digits are
 * too many for one ("0761/123456" is one, "09422058997/09177107080" two).
 * A leading bracket that never closes is dropped.
 */
private fun phoneParts(text: String, range: IntRange): List<IntRange> {
    val candidate = text.substring(range)
    val parts = if (candidate.count { it.isDigit() } > MAX_PHONE_DIGITS) {
        var start = range.first
        candidate.split('/').map { part -> (start until start + part.length).also { start += part.length + 1 } }
    } else {
        listOf(range)
    }
    return parts.map { part ->
        var first = part.first
        var last = part.last
        while (first <= last && text[first] == ' ') first++
        while (last >= first && text[last] == ' ') last--
        if (first <= last && text[first] == '(' && ')' !in text.substring(first, last + 1)) first++
        first..last
    }.filter { !it.isEmpty() }
}

/** OCR output without trailing spaces, with at most one blank line in a row, trimmed. */
fun cleanRecognisedText(raw: String): String =
    raw.lines().joinToString("\n") { it.trimEnd() }.replace(Regex("\n{3,}"), "\n\n").trim()
