package gov.nasa.gsfc.icesat2.icesat_2

import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.Parcelize
import java.util.*

@Parcelize
class Point(val dateString: String, val dayOfWeek: String, val date: String, val year: String,
            val time: String, val ampm: String, val timezone: String, val longitude: Double, val latitude: Double, val dateObject: Date) :
    Parcelable {
    companion object {
        private const val TAG = "Point"
    }

    override fun toString(): String {
        Log.d(TAG, "toString - Point")
        return """
            $dayOfWeek $date $year $time $ampm $timezone 
        """.trimIndent()
    }
}