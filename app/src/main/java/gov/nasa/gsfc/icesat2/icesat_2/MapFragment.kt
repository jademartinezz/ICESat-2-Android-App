@file:Suppress("OVERRIDE_DEPRECATION")

package gov.nasa.gsfc.icesat2.icesat_2

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color.parseColor
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.CheckBox
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
// import kotlinx.android.synthetic.main.fragment_map.*     // DEPRECATED LANGUAGE
import kotlin.math.abs
import com.google.android.gms.maps.model.Marker
import gov.nasa.gsfc.icesat2.icesat_2.databinding.FragmentMapBinding // Import generated binding class
import kotlin.math.ln
private const val TAG = "MapFragment"
private const val T2 = "EdwardSecondTag"
private const val BUNDLE_MAP_OPTIONS = "BundleMapOptions"

class MapFragment : Fragment(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener, GoogleMap.OnMapClickListener, IMarkerSelectedCallback,
GoogleMap.OnPolylineClickListener, IShareAndCalendar {

    private lateinit var constraintLayoutPastFuture: ConstraintLayout
    private lateinit var binding: FragmentMapBinding // Declare binding variable
    private lateinit var mMap: GoogleMap
    private lateinit var pointList: ArrayList<Point>
    private var pastFutureThreshold = 0
    // private lateinit var pastPointList: ArrayList<Point> // UNUSED VARIABLE
    private var searchCenter: LatLng? = null  // Declare as nullable
    private var searchRadius: Double = -1.0
    private lateinit var fm: FragmentManager
    private lateinit var markerSelectedFragment: MarkerSelectedFragment
    private var marker: Marker? = null //used to keep track of the selected marker
    private var count = 0 //to access the point array based on the marker later
    private var markerList = ArrayList<Marker>()
    private val polylineList = ArrayList<Polyline>()
    private val laserBeamList = ArrayList<PolylineOptions>()
    private lateinit var laserPolyLines: ArrayList<Polyline>
    private val flyoverDatesAndTimes = ArrayList<String>()
    private var markersPlotted = false //have the markers already been added to the map
    private val offsets = arrayOf(-3390, -3300, -47, 47, 3300, 3390) //for calculating the position of the laser beam
    private var bundleString: String? = null
    private lateinit var checkBoxMarker: CheckBox
    private lateinit var checkBoxPath: CheckBox
    private lateinit var checkBoxLasers: CheckBox

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "START onCreateView - MapFragment")
        binding = FragmentMapBinding.inflate(inflater, container, false)
        Log.d(TAG, "END onCreateView - MapFragment")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "START onViewCreated - MapFragment")
        super.onViewCreated(view, savedInstanceState)
        constraintLayoutPastFuture = view.findViewById(R.id.constraintLayoutPastFuture)
        checkBoxMarker = view.findViewById(R.id.checkBoxMarker)
        checkBoxPath = view.findViewById(R.id.checkBoxPath)
        checkBoxLasers = view.findViewById(R.id.checkBoxLasers)

        setHasOptionsMenu(true) // DEPRECATED, ACCOMMODATIONS BY onCreateOptionsMenu
        fm = childFragmentManager

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val mainActivityViewModel = MainActivity.getMainViewModel()

        Log.d(TAG, "CHECKING PAST AND future values")
        Log.d(TAG, "size ${MainActivity.getMainViewModel()?.pastPointsList?.value?.size}")
        Log.d(TAG, "${MainActivity.getMainViewModel()?.allPointsList?.value?.size}")

        pointList = arrayListOf()
        if (mainActivityViewModel?.pastPointsList?.value != null) {
            pointList.addAll(mainActivityViewModel.pastPointsList.value!!)
            pastFutureThreshold = mainActivityViewModel.pastPointsList.value!!.size - 1
        }

        if (mainActivityViewModel?.allPointsList?.value != null) {
            pointList.addAll(mainActivityViewModel.allPointsList.value!!)
        }
        Log.d(TAG, "pointList size is ${pointList.size}")
        if (this::mMap.isInitialized) {
            addChainPolyline(pointList)
        }

        //check if there are both future and past points
        if (pastFutureThreshold != -1 && pastFutureThreshold + 1 < pointList.size) {
            constraintLayoutPastFuture.visibility = View.VISIBLE
        }

        binding.textViewSeeAll.setOnClickListener {
            binding.textViewSeeAll.visibility = View.INVISIBLE
            pointList = (mainActivityViewModel?.allPointsList?.value ?: emptyList()) as ArrayList<Point>
            // Clear everything and redraw everything
            count = 0
            markerList.clear()
            polylineList.clear()
            laserBeamList.clear()
            flyoverDatesAndTimes.clear()
            addChainPolyline(pointList)
        }

        mainActivityViewModel?.getSearchCenter()?.observe(viewLifecycleOwner) { center ->
            center?.let {
                Log.d(TAG, "MapFragment searchCenterObserved to be ${it.latitude}, ${it.longitude}")
                searchCenter = it
            }
        }

        mainActivityViewModel?.getSearchRadius()?.observe(viewLifecycleOwner) { radius ->
            radius?.let {
                Log.d(TAG, "MapFrag searchRadius observed to be $it")
                searchRadius = it
            }
        }

        checkBoxMarker.setOnClickListener {
            if (checkBoxMarker.isChecked) {
                markerList.forEach {
                    it.isVisible = true
                }
            } else {
                markerList.forEach {
                    it.isVisible = false
                }
            }
        }

        checkBoxPath.setOnClickListener {
            if (checkBoxPath.isChecked) {
                polylineList.forEach {
                    it.isVisible = true
                }
            } else {
                polylineList.forEach {
                    it.isVisible = false
                }
            }
        }

        laserPolyLines = ArrayList()

        checkBoxLasers.setOnClickListener {
            if (checkBoxLasers.isChecked && laserBeamList.isEmpty()) {
                calculateLaserBeams()
                populateLaserPolyLines()
            }
            if (checkBoxLasers.isChecked) {
                laserPolyLines.forEach {
                    it.isVisible = true
                }
            } else {
                laserPolyLines.forEach {
                    it.isVisible = false
                }
            }
        }

    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        Log.d(TAG, "START onViewStateRestored - MapFragment")
        super.onViewStateRestored(savedInstanceState)
        bundleString = savedInstanceState?.getString(BUNDLE_MAP_OPTIONS)
        correctForStateIfNeeded()
        Log.d(TAG, "END onViewStateRestored - MapFragment")
    }


    override fun onMapReady(googleMap: GoogleMap) {
        Log.d(TAG, "onMapReady - MapFragment")
        mMap = googleMap
        mMap.uiSettings.isMapToolbarEnabled = false
        mMap.uiSettings.isZoomControlsEnabled = false
        mMap.setOnMarkerClickListener(this)
        mMap.setOnMapClickListener(this)
        mMap.setOnPolylineClickListener(this)
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            || ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
        }
        //future points
        if (this::pointList.isInitialized && !markersPlotted) {
            Log.d(TAG, "Adding poly-lines inside onMapReady")
            markersPlotted = true
            addChainPolyline(pointList)
        }
    }

    private fun addChainPolyline(chain: ArrayList<Point>) {
        Log.d(TAG, "addChainPolyLine Starts")
        var polylineTag = 0
        var polylineOptions = PolylineOptions()

        val pastColor = 0xff007fff.toInt()

        count = 0
        //adding a marker at each point - maybe polygons are a better way to do this
        val myMarker = MarkerOptions()
        for (i in 0 until chain.size) {
            //check if the point is on the same chain as the previous point
            if (i != 0 && !onSameChain(chain[i - 1], chain[i])) {
                if (count <= pastFutureThreshold + 1) {
                    drawPolyline(polylineOptions, polylineTag, pastColor)
                } else {
                    drawPolyline(polylineOptions, polylineTag)
                }
                polylineTag = count
                polylineOptions = PolylineOptions()
            }
            polylineOptions.add(LatLng(chain[i].latitude, chain[i].longitude))
            //add marker to map and markerList


            val markerAdded = mMap.addMarker(

                myMarker.position(LatLng(chain[i].latitude, chain[i].longitude))
                    .title(getString(R.string.latLngDisplayString, chain[i].latitude.toString(), 0x00B0.toChar(), chain[i].longitude.toString(), 0x00B0.toChar())))


            if (count <= pastFutureThreshold) {
                //markerAdded.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                markerAdded?.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            } else {
                markerAdded?.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            }

            if (markerAdded != null) {
                markerAdded.tag = count
            }
            if (markerAdded != null) {
                markerList.add(markerAdded)
            }
            count++
        }
        Log.d(TAG, "LAST TIME $count and thresh $pastFutureThreshold")
        if (count <= pastFutureThreshold + 1) { // +1 because count gets incremented before this check
            drawPolyline(polylineOptions, polylineTag, pastColor)
        } else {
            drawPolyline(polylineOptions, polylineTag)
        }
        addCircleRadius(searchRadius)

        correctForStateIfNeeded()
    }

    private fun correctForStateIfNeeded() {
        Log.d(TAG, "correctForStateIfNeeded - MapFragment")
        if (bundleString != null) {
            Log.d(T2, "string is $bundleString")
            try {
                val splitString = bundleString!!.split(",")
                val markers = splitString[0]
                val tracks = splitString[1]
                val lasers = splitString[2]

                checkBoxMarker.isChecked = markers == "true"
                Log.d(T2, "markers $markers")
                if (!checkBoxMarker.isChecked) {
                    Log.d(T2, "click marker checkbox")
                    markerList.forEach {
                        it.isVisible = false
                    }
                }
                checkBoxPath.isChecked = tracks == "true"
                Log.d(T2, "paths $tracks")
                if (!checkBoxPath.isChecked) {
                    Log.d(T2, "click path checkbox")
                    polylineList.forEach {
                        it.isVisible = false
                    }
                }
                checkBoxLasers.isChecked = lasers == "true"

                Log.d(T2, "lasers $lasers")
                if (checkBoxLasers.isChecked) {
                    if (laserPolyLines.isEmpty()) {
                        calculateLaserBeams()
                        populateLaserPolyLines()
                    }
                }

            } catch (e: Exception) {
                Log.d(T2, "caught exception ${e.message}")
            }
        }
    }

    private fun onSameChain(p1: Point, p2: Point): Boolean {
        Log.d(TAG, "onSameChain - MapFragment")
        val timingThreshold = 60
        return abs(p1.dateObject.time - p2.dateObject.time) < timingThreshold * 1000
    }

    private fun drawPolyline(polylineOptions: PolylineOptions, tagValue: Int, lineColor: Int = 0xff32CD32.toInt() ) {
        Log.d(TAG, "drawPolyline - MapFragment")
        Log.d(TAG, "Adding polyline with tag $tagValue")
        polylineList.add(mMap.addPolyline(polylineOptions).apply {
            jointType = JointType.ROUND
            color = lineColor
            isClickable = true
            tag = tagValue
        })
        //add date to flyover date
        flyoverDatesAndTimes.add(
            String.format(
                "%s (%.5s %s)",
                pointList[tagValue].date,
                pointList[tagValue].time,
                pointList[tagValue].ampm
            )
        )
    }

    private fun addCircleRadius(radius: Double) {
        Log.d(TAG, "addCircleRadius - MapFragment")
        val milesToMeters = 1609.34
        val circleOptions = searchCenter?.let {
            CircleOptions().radius(radius * milesToMeters).center(it)
        }
        if (circleOptions != null) {
            mMap.addCircle(circleOptions)
        }

        val circle = circleOptions?.let { mMap.addCircle(it) }

        if (circleOptions != null) {
            circleOptions.center?.let {
                CameraUpdateFactory.newLatLngZoom(
                    it,
                    getZoomLevel(circle)
                )
            }?.let {
                mMap.moveCamera(
                    it
                )
            }
        }
    }

    private fun getZoomLevel(circle: Circle?): Float {
        Log.d(TAG, "getZoomLevel - MapFragment")
        var zoomLevel = 11.0
        if (circle != null) {
            val radius = circle.radius + circle.radius / 2
            val scale = radius / 500
            zoomLevel = (16.25 - ln(scale) / ln(2.0))
        }
        return zoomLevel.toFloat()
    }

    override fun onMarkerClick(p0: Marker): Boolean {
        Log.d(TAG, "onMarkerClick - MapFragment")
        constraintLayoutPastFuture.visibility = View.INVISIBLE
        marker = p0
        val markerTag = marker?.tag as Int
        showMarkerDisplayFragment(markerTag, true)
        return false
    }

    private fun showMarkerDisplayFragment(tagValue: Int, isMarker: Boolean) {
        Log.d(TAG, "showMarkerDisplayFragment - MapFragment")
        val fragmentTransaction = fm.beginTransaction()
        markerSelectedFragment = MarkerSelectedFragment.newInstance(pointList[tagValue], isMarker)
        fragmentTransaction.apply {
            setCustomAnimations(R.anim.slide_in_down, R.anim.blank_animation)
            replace(R.id.mapFragmentContainer, markerSelectedFragment)
            commit()
        }
    }

    override fun onMapClick(p0: LatLng) {
        Log.d(TAG, "onMapClick - MapFragment")
        marker = null
        if (this::markerSelectedFragment.isInitialized) {
            fm.beginTransaction().apply {
                setCustomAnimations(R.anim.blank_animation, R.anim.slide_out_down)
                replace(R.id.mapFragmentContainer, DummyFragment())
                commit()
            }
            constraintLayoutPastFuture.visibility = View.VISIBLE
        }
    }

    override fun onPolylineClick(p0: Polyline) {
        Log.d(TAG, "onPolylineClick - MapFragment")
        Log.d(
            TAG,
            "polylineClicked. Tag is ${p0.tag} chain date is ${pointList[p0.tag as Int].dateString}"
        )
        showMarkerDisplayFragment(p0.tag as Int, false)
    }

    override fun closeButtonPressed() {
        Log.d(TAG, "closeButtonPressed - MapFragment")
//        onMapClick(p0 = null)
    }

    private fun calculateLaserBeams() {
        Log.d(TAG, "START calculateLaserBeams - MapFragment")
        Log.d(TAG, "Calculating laser beams")

        var laserBeamListIndex = -1 // will be incremented to zero on the first pass through the loop
        //calculating all the left sides

        for (element in offsets) {
            for (count in 0 until pointList.size) {
                val latN = pointList[count].latitude
                val long = pointList[count].longitude
                val newLong = degreesOfLong(element, latN)

                if (count == 0 || !onSameChain(pointList[count], pointList[count - 1])) {
                    //Log.d(TAG, "count = $count creating a new polyline for the laser beams")
                    laserBeamList.add(PolylineOptions())
                    laserBeamListIndex++
                }

                laserBeamList[laserBeamListIndex] =
                    laserBeamList[laserBeamListIndex].add(LatLng(latN, long + newLong))
            }
        }
        Log.d(TAG, "START calculateLaserBeams - MapFragment")
    }

    private fun populateLaserPolyLines() {
        Log.d(TAG, "populateLaserPolyLines - MapFragment")
        val colorsArr = requireContext().resources.getStringArray(R.array.greenColors)
        val dash: PatternItem = Dash(30F)
        val gap: PatternItem = Gap(20F)
        val dashedPolyline: List<PatternItem> = listOf(gap, dash)

        val indo = laserBeamList.size / offsets.size //diving by the number of entries in offset
        Log.d(TAG, "indo is $indo. laserBeamLIst size ${laserBeamList.size} offsets.size = ${offsets.size}")
        if (this::mMap.isInitialized) {
            for (i in laserBeamList.indices) {
                if (indo < colorsArr.size) {
                    Log.d(TAG, "plotting color i % indo ${i % indo}")
                    laserPolyLines.add(mMap.addPolyline(laserBeamList[i].color(parseColor(colorsArr[i % indo])).pattern(dashedPolyline)))
                } else {
                    Log.d(TAG, "plotting color sie - 1 :(")
                    laserPolyLines.add(mMap.addPolyline(laserBeamList[i].color(parseColor(colorsArr[(i % indo) % colorsArr.size])).pattern(dashedPolyline)))
                }
            }
        }
    }

    private fun degreesOfLong(distance: Int, lat: Double): Double {
        Log.d(TAG, "degreesOfLong - MapFragment")
        return  distance / (kotlin.math.cos(Math.toRadians(lat)) * 111000)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        Log.d(TAG, "onCreateOptionsMenu - MapFragment")
        inflater.inflate(R.menu.main_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "onOptionsItemSelected - MapFragment")
        return when (item.itemId) {
            R.id.menuAddToCalendar -> {
                attemptToAddToCalendar()
                true // Indicate that the item is handled
            }
            R.id.menuShare -> {
                (this as IShareAndCalendar).showShareScreen(mMap, requireActivity(), requireContext(), flyoverDatesAndTimes)
                true // Indicate that the item is handled
            }
            else -> {
                // Handle other menu items here or log an error
                false // Indicate that the item is not handled
            }
        }
    }

    private fun attemptToAddToCalendar() {
        Log.d(TAG, "attemptToAddToCalendar - MapFragment")
        if (marker != null) {
            val markerTag = marker?.tag as Int
            Log.d(TAG, "markerTag is $markerTag; Point is ${pointList[markerTag]}")
            addToCalendar(requireContext(),
                getString(R.string.icesatFlyover),
                pointList[markerTag].dateObject.time,
                pointList[markerTag].latitude,
                pointList[markerTag].longitude
            )
        } else {
            Toast.makeText(requireContext(), getString(R.string.selectALocation), Toast.LENGTH_LONG).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        Log.d(TAG, "onSaveInstanceState - MapFragment")
        super.onSaveInstanceState(outState)
        val mapBundleValues = "${checkBoxMarker.isChecked},${checkBoxPath.isChecked},${checkBoxLasers.isChecked}"
        outState.putString(BUNDLE_MAP_OPTIONS, mapBundleValues)
    }
}