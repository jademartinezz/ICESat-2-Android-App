package gov.nasa.gsfc.icesat2.icesat_2.ui.search

import android.widget.EditText

interface ISearchFragmentCallback {
    var editTextLat: EditText
    var editTextLon: EditText

    fun searchButtonPressed(lat: Double, long: Double, radius: Double, calledFromSelectOnMap: Boolean, time: Long = -1L, pastResults: Boolean = false, futureResults: Boolean = true)

    fun useCurrentLocationButtonPressed(simpleSearch: Boolean)

    fun selectOnMapButtonPressed()

    fun trackButtonPressed()
}