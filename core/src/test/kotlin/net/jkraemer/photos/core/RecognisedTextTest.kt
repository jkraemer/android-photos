package net.jkraemer.photos.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RecognisedTextTest {
    /** Each link as the text it covers and the URI it opens. */
    private fun links(text: String) = findLinks(text).map { text.substring(it.range) to it.target }

    @Test fun `splits numbers joined by slashes when together they are too long for one`() = assertEquals(
        listOf(
            "(035)5272818" to "tel:0355272818",
            "09422058997" to "tel:09422058997",
            "09177107080" to "tel:09177107080",
        ),
        links("Tampi 11 tel.nos.(035)5272818/09422058997/09177107080 oN Bate Port Ce :"),
    )

    @Test fun `finds the flyer's cell numbers`() = assertEquals(
        listOf("09957429363" to "tel:09957429363", "09957429369" to "tel:09957429369"),
        links("Ce No. 09957429363 == Call Cell No. 09957429369 (Sibulan)"),
    )

    @Test fun `keeps a German area code and number joined by a slash`() =
        assertEquals(listOf("0761/123456" to "tel:0761123456"), links("Tel. 0761/123456"))

    @Test fun `reads German spacing and dashes`() = assertEquals(
        listOf("0761 123456" to "tel:0761123456", "0171-1234567" to "tel:01711234567"),
        links("Büro 0761 123456, mobil 0171-1234567"),
    )

    @Test fun `drops the zero in brackets after a country code`() =
        assertEquals(listOf("+49 (0)761 12 34 56" to "tel:+49761123456"), links("+49 (0)761 12 34 56"))

    @Test fun `times, dates, years and prices are not phone numbers`() = assertEquals(
        emptyList<Pair<String, String>>(),
        links("4:30 AM 12:00 PM 10:30 22.10.2026 2025 - 2026 9,99 € 79098 Freiburg"),
    )

    @Test fun `too short or too long is not a phone number`() =
        assertEquals(emptyList<Pair<String, String>>(), links("0761 12 and 0123456789012345678"))

    @Test fun `finds emails and urls`() = assertEquals(
        listOf(
            "info@maayo.ph" to "mailto:info@maayo.ph",
            "www.maayo.ph" to "https://www.maayo.ph",
            "https://example.com/a?b=1" to "https://example.com/a?b=1",
        ),
        links("Mail info@maayo.ph, web www.maayo.ph. Or https://example.com/a?b=1."),
    )

    @Test fun `a number inside a url is not a phone number`() =
        assertEquals(listOf("https://x.de/07611234567" to "https://x.de/07611234567"), links("https://x.de/07611234567"))

    @Test fun `cleaning collapses blank lines and trims`() =
        assertEquals("A\n\nB", cleanRecognisedText("\n  A  \n\n \n\nB \n\n"))

    @Test fun `cleaning whitespace only gives an empty text`() =
        assertEquals("", cleanRecognisedText(" \n \n"))
}
