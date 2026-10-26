package net.jkraemer.photos.media

import android.content.Context
import android.location.Address
import android.location.Geocoder
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import net.jkraemer.photos.core.GeoPoint
import net.jkraemer.photos.core.geoPointOrNull
import net.jkraemer.photos.core.placeName

/**
 * Place names from the system's geocoder. The lookup runs in the OS (on
 * GrapheneOS its opt-in geocoder), so the app itself stays offline. A
 * [geocoder] of null means the system has none.
 */
class GeocoderPlaceNames(private val geocoder: Geocoder?) {
    /** The place at [point], or null when there is no geocoder, no address or an error. */
    suspend fun placeName(point: GeoPoint): String? {
        val geocoder = geocoder ?: return null
        // The geocoder throws on a point off the globe; such a point has no name.
        if (geoPointOrNull(point.latitude, point.longitude) == null) return null
        val address = suspendCancellableCoroutine<Address?> { continuation ->
            geocoder.getFromLocation(
                point.latitude,
                point.longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        continuation.resume(null)
                    }
                },
            )
        } ?: return null
        return placeName(address.featureName, address.locality, address.subAdminArea, address.adminArea, address.countryName)
    }

    companion object {
        fun of(context: Context) = GeocoderPlaceNames(if (Geocoder.isPresent()) Geocoder(context) else null)
    }
}
