package gov.nasa.gsfc.icesat2.icesat_2

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.TypeEvaluator
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.util.Property
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
// import kotlinx.android.synthetic.main.fragment_satellite_tracking.* // DEPRECATED LANGUAGE
import kotlinx.coroutines.*
import java.net.URL
import kotlin.math.abs
import kotlin.math.sign

private const val TAG = "SatelliteTrackingFragment"

private const val ZOOM_LEVEL = 1F

class SatelliteTrackingFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    //one point every five seconds
    private lateinit var satellitePos: ArrayList<TrackingPoint>
    private lateinit var satelliteMarker: Marker
    private var continueAnimating = true
    private var count = 0
    private lateinit var animator: ObjectAnimator
    private lateinit var textViewDisplayCoords: TextView // Declare textViewDisplayCoords here
    private lateinit var textViewNearPole: TextView // Declare textViewNearPole here

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView - SatelliteTrackingFragment")
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_satellite_tracking, container, false)
        textViewDisplayCoords = view.findViewById(R.id.textViewDisplayCoords) // Initialize textViewDisplayCoords
        textViewNearPole = view.findViewById(R.id.textViewNearPole) // Initialize textViewNearPole
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "onViewCreated - SatelliteTrackingFragment")
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated - SatelliteTrackingFragment")
        MainActivity.getMainViewModel()?.getTrackingData()?.observe(viewLifecycleOwner) {
            it.also { satellitePos = it }
            Log.d(TAG, "satellitePos.size is ${satellitePos.size}")
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Assuming textViewMoreData is a TextView
        val textViewMoreData = view.findViewById<TextView>(R.id.textViewMoreData)
        textViewMoreData?.setOnClickListener {
            // Handle click event here
            downloadMoreData()
        }
    }


    override fun onResume() {
        Log.d(TAG, "START onResume - SatelliteTrackingFragment")
        super.onResume()
        if (this::mMap.isInitialized) {
            continueAnimating = true
            initializeAnimation()
        }
        Log.d(TAG, "END onResume - SatelliteTrackingFragment")
    }

    override fun onMapReady(p0: GoogleMap) {
        Log.d(TAG, "onMapReady - SatelliteTrackingFragment")
        mMap = p0
        initializeAnimation()
    }

    private fun initializeAnimation() {
        Log.d(TAG, "START initializeAnimation - SatelliteTrackingFragment")
        //determine position in list (useful in leave app and then come back)
        val currentTime = System.currentTimeMillis()
        while (count < satellitePos.size - 2 && currentTime > satellitePos[count + 1].timeInMillis) {
            count++
        }
        if (count >= satellitePos.size - 2) {
            downloadMoreData()
        }
        Log.d(TAG, "After while loop, count is $count")

        drawSatellitePosPolyline()

        val currentTimeInMillis = System.currentTimeMillis()
        Log.d(TAG, "===================")
        Log.d(TAG, "start: ${satellitePos[0].timeInMillis}")
        Log.d(TAG, "curr : $currentTimeInMillis")
        Log.d(TAG, "end 1: ${satellitePos[1].timeInMillis}")
        Log.d(TAG, "===================")

        //LatLng and Double are the only types that will ever be put in the array, so casts are safe
        var calculatePositionArray = calculateStartingPosition(count)
        var startingPosition = calculatePositionArray[0] as LatLng
        var percentage = calculatePositionArray[1] as Double

        while (percentage > 1 && count < satellitePos.size - 2) {
            Log.d(TAG, "While loop running with percentage $percentage and count $count")
            count++
            calculatePositionArray = calculateStartingPosition(count)
            startingPosition = calculatePositionArray[0] as LatLng
            percentage = calculatePositionArray[1] as Double
        }

        val durationForFirstSegment = calculateTimeIncrement(count) * (1 - percentage)
        Log.d(TAG, "duration for first segment is ${durationForFirstSegment.toLong()}")

        //if we don't have a marker, add one. Otherwise move the current marker
        if (!this::satelliteMarker.isInitialized) {
            satelliteMarker = mMap.addMarker(
                MarkerOptions().position(startingPosition).icon(
                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)))!!
        } else {
            satelliteMarker.position = startingPosition
        }

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startingPosition, ZOOM_LEVEL))

        animateMarkerToICS(satelliteMarker, count, durationForFirstSegment.toLong())
        Log.d(TAG, "END initializeAnimation - SatelliteTrackingFragment")
    }

    private fun drawSatellitePosPolyline() {
        Log.d(TAG, "drawSatellitePosPolyline - SatelliteTrackingFragment")
        CoroutineScope(Dispatchers.Main).launch {
            val polylineOptions = PolylineOptions().geodesic(true)
            for (element in satellitePos) {
                polylineOptions.add(LatLng(element.lat, element.long))
            }
            mMap.addPolyline(polylineOptions)
        }
    }

    /**
     * @return an array of (LatLng, percentage) where LatLng is the starting LatLng and percentage is
     * an approximation of how much of the distance between the the first LatLng (before now) and the
     * second LatLng(after now) the satellite has covered
     */
    private fun calculateStartingPosition(startingIndex: Int) : Array<Any> {
        Log.d(TAG, "calculateStartingPosition - SatelliteTrackingFragment")
        val currentTimeInMillis = System.currentTimeMillis()
        Log.d(TAG, "Current time in millis: $currentTimeInMillis")
        Log.d(TAG, "satellite pos: $satellitePos")
        Log.d(TAG, "starting index: $startingIndex")

        val numerator = currentTimeInMillis - satellitePos[startingIndex].timeInMillis
        val denominator = satellitePos[startingIndex + 1].timeInMillis - satellitePos[startingIndex].timeInMillis
        val percentage = (numerator.toDouble() / denominator)
        Log.d(TAG, "num $numerator denom $denominator percentage is $percentage")

        val dlat = satellitePos[startingIndex + 1].lat - satellitePos[startingIndex].lat
        val dlong = satellitePos[startingIndex + 1].long - satellitePos[startingIndex].long
        val startLat = satellitePos[startingIndex].lat + percentage * dlat
        val startLong = satellitePos[startingIndex].long + percentage * dlong
        Log.d(TAG, "dlat $dlat; dlong $dlong")
        Log.d(TAG, "beginLat ${satellitePos[startingIndex].lat} endLat ${satellitePos[startingIndex + 1].lat}, resLat $startLat")
        Log.d(TAG, "beginLong ${satellitePos[startingIndex].long} endLong ${satellitePos[startingIndex + 1].long}, resLat $startLong")

        return arrayOf(LatLng(startLat, startLong), percentage)
    }

    private fun animateMarkerToICS(marker: Marker, finalMarkerCount: Int, duration: Long) {
        Log.d(TAG, "animateMarkerToICS - SatelliteTrackingFragment")

        // Check if duration is negative
        if (duration < 0) {
            showErrorDialog()
            return // Exit the function to avoid starting the animation
        }

        val finalPosition = LatLng(satellitePos[finalMarkerCount + 1].lat, satellitePos[finalMarkerCount + 1].long)
        val typeEvaluator =
            TypeEvaluator<LatLng> { fraction, startValue, endValue -> interpolate(fraction, startValue, endValue) }

        val property: Property<Marker, LatLng> = Property.of(Marker::class.java, LatLng::class.java, "position")
        animator = ObjectAnimator.ofObject(marker, property, typeEvaluator, finalPosition)

        animator.duration = duration
        animator.interpolator = LinearInterpolator()

        animator.start()
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(p0: Animator) {}
            override fun onAnimationCancel(p0: Animator) {}
            override fun onAnimationRepeat(p0: Animator) {}

            override fun onAnimationEnd(p0: Animator) {
                Log.d(TAG, "onAnimationEnd - SatelliteTrackingFragment")
                Log.d(
                    TAG,
                    "reached ${satellitePos[count + 1].lat}, ${satellitePos[count + 1].long} at ${System.currentTimeMillis()}. diff is ${System.currentTimeMillis() - satellitePos[count + 1].timeInMillis}"
                )
                if (continueAnimating && count + 2 < satellitePos.size) {
                    count++
                    Log.d(TAG, "animation ends count is now $count")
                    animateMarkerToICS(satelliteMarker, count, calculateTimeIncrement(count))

                    if (count > satellitePos.size - 3) {
                        downloadMoreData()
                    }
                } else {
                    Log.d(TAG, "all trackingData has been animated and shown")
                }
            }
        })
    }

    private fun showErrorDialog() {
        // Implement this method to show an error dialog or message to the user
        AlertDialog.Builder(requireContext())
            .setTitle("Servers not updated.")
            .setMessage("We are working on updating our servers. Please try again later.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun calculateTimeIncrement(startingIndex: Int): Long {
        Log.d(TAG, "calculateTimeIncrement - SatelliteTrackingFragment")
        return satellitePos[count + 1].timeInMillis - satellitePos[startingIndex].timeInMillis
    }

    private fun interpolate(fraction: Float, a: LatLng, b: LatLng): LatLng {
        Log.d(TAG, "interpolate - SatelliteTrackingFragment")
        val lat = (b.latitude - a.latitude) * fraction + a.latitude
        var lngDelta = b.longitude - a.longitude

        // Take the shortest path across the 180th meridian.
        if (abs(lngDelta) > 180) {
            lngDelta -= sign(lngDelta) * 360
        }
        val lng = lngDelta * fraction + a.longitude
        try {
            textViewDisplayCoords.text = String.format("Lat: %.2f${0x00B0.toChar()}N\nLon: %.2f${0x00B0.toChar()}E", lat, lng)
            when {
                lat > 83 -> {
                    textViewNearPole.text = getString(R.string.nearNorthPole)
                    textViewNearPole.visibility = View.VISIBLE
                }
                lat < -83 -> {
                    textViewNearPole.text = getString(R.string.nearSouthPole)
                    textViewNearPole.visibility = View.VISIBLE
                }
                textViewNearPole.isVisible -> {
                    textViewNearPole.visibility = View.INVISIBLE
                }
            }
        } catch (e: Exception) { Log.d(TAG, "set text CATCH BLOCK") }
        return LatLng(lat, lng)
    }

    fun downloadMoreData() {
        Log.d(TAG, "downloadMoreData - SatelliteTrackingFragment")
        val lastTimeInCurrentData = satellitePos[satellitePos.size - 1].timeInMillis + 1
        val numResults = 30
        val downloadLink = "http://icesat2apptracking-env.eba-maqwzufh.us-east-1.elasticbeanstalk.com/find?time=$lastTimeInCurrentData&numResults=$numResults"
        //0th entry will be the same but all of the following will be different so we can just append the different ones
        try {
            val url = URL(downloadLink)
            val downloadData = DownloadData(url, requireContext())
            CoroutineScope(Dispatchers.IO).launch {
                val newData: Deferred<ArrayList<TrackingPoint>> = async {
                    downloadData.downloadTrackingData(url, requireActivity() as MainActivity)
                }

                if (newData.await().isNotEmpty()) {
                    Log.d(TAG, "previous size of satellitePos is ${satellitePos.size}")
                    for (i in 1 until newData.await().size) {
                        satellitePos.add(newData.await()[i])
                    }
                    Log.d(TAG, "new size of satellitePos is ${satellitePos.size}")
                    //remove the duplicates
                    Log.d(TAG, "about to draw polyline")
                    drawSatellitePosPolyline()
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Exception trying to download more data")
        }
    }

    override fun onStop() {
        Log.d(TAG, "onStop - SatelliteTrackingFragment")
        super.onStop()
        continueAnimating = false

        // Check if the animator has been initialized before attempting to cancel it
        if (this::animator.isInitialized) {
            Log.d(TAG, "cancelling animator")
            animator.cancel()
        } else {
            Log.d(TAG, "animator not initialized")
        }

        Log.d(TAG, "onStop - SatelliteTrackingFragment completed")
    }
}
