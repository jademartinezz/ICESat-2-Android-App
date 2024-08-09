@file:Suppress("DEPRECATION")

package gov.nasa.gsfc.icesat2.icesat_2

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.util.Log

interface IGeocoding {
    companion object {
        private const val TAG = "IGeocoding"
    }

    fun getAddress(context: Context, lat: Double, long: Double): String {
        Log.d(TAG, "getAddress - IGeocoding")
        val geocoder = Geocoder(context)
        val addresses: List<Address> = geocoder.getFromLocation(lat, long, 1) ?: emptyList()

        return try {
            if (addresses.isNotEmpty()) {
                addresses[0].getAddressLine(0) ?: context.getString(R.string.unknownLocation)
            } else {
                context.getString(R.string.unknownLocation)
            }
        } catch (e: Exception) {
            context.getString(R.string.unknownLocation)
        }
    }

    fun getGeographicInfo(geocoder: Geocoder, lat: Double, long: Double): String {
        Log.d(TAG, "getGeographicInfo - IGeocoding")
        val address = geocoder.getFromLocation(lat, long, 1)

        if (address != null) {
            if (address.size == 0) {
                return "Unknown Location"
            }
        }

        //returns {locality, 'state', country}
        var locationString = ""

        if (!address?.get(0)?.locality.isNullOrEmpty()) {
            locationString += "${address?.get(0)?.locality}, "
        }
        if (!address?.get(0)?.adminArea.isNullOrEmpty()) {
            locationString += "${address?.get(0)?.adminArea}, "
        }
        if (!address?.get(0)?.countryName.isNullOrEmpty()) {
            locationString += "${address?.get(0)?.countryName}; "
        }

        if (locationString == "") {
            locationString = "Unknown Location, "
        }

        return locationString
    }
}