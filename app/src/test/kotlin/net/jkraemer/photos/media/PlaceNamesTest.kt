package net.jkraemer.photos.media

import android.location.Address
import android.location.Geocoder
import java.util.Locale
import kotlinx.coroutines.runBlocking
import net.jkraemer.photos.core.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PlaceNamesTest {
    private val fehmarn = GeoPoint(54.5021, 11.2312)
    private val geocoder = Geocoder(RuntimeEnvironment.getApplication())

    @Test fun `an address becomes its place name`() {
        val address = Address(Locale.UK).apply {
            locality = "Burg auf Fehmarn"
            subAdminArea = "Ostholstein"
            countryName = "Germany"
        }
        shadowOf(geocoder).setFromLocation(listOf(address))
        assertEquals("Burg auf Fehmarn, Germany", runBlocking { GeocoderPlaceNames(geocoder).placeName(fehmarn) })
    }

    @Test fun `no address means no name`() {
        shadowOf(geocoder).setFromLocation(emptyList())
        assertNull(runBlocking { GeocoderPlaceNames(geocoder).placeName(fehmarn) })
    }

    @Test fun `no geocoder means no name`() =
        assertNull(runBlocking { GeocoderPlaceNames(null).placeName(fehmarn) })

    @Test fun `a geocoder error means no name`() {
        shadowOf(geocoder).setErrorMessage("geocoder unavailable")
        assertNull(runBlocking { GeocoderPlaceNames(geocoder).placeName(fehmarn) })
    }

    @Test fun `a point the geocoder rejects means no name`() {
        shadowOf(geocoder).setFromLocation(emptyList())
        assertNull(runBlocking { GeocoderPlaceNames(geocoder).placeName(GeoPoint(91.0, 11.2312)) })
    }
}
