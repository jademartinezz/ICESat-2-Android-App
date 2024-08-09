package gov.nasa.gsfc.icesat2.icesat_2

import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

private const val TAG = "SingleMarkerMap"

class SingleMarkerMap : Fragment(), IShareAndCalendar, OnMapReadyCallback, MenuItem.OnMenuItemClickListener {
    private lateinit var mMap: GoogleMap
    private val args by navArgs<SingleMarkerMapArgs>()
    private lateinit var title: String
    private var lat = 0.0
    private var long = 0.0
    private var dateObjectTime = 0L
    private var markerDisplayed = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView - SingleMarkerMap")
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_single_marker_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "START onViewCreated - SingleMarkerMap")
        super.onViewCreated(view, savedInstanceState)
        lat = args.lat.toDouble()
        long = args.long.toDouble()
        title = args.title
        dateObjectTime = args.dateObjectTime
        setHasOptionsMenu(true)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        Log.d(TAG, "END onViewCreated - SingleMarkerMap")
    }

    override fun onMapReady(p0: GoogleMap) {
        Log.d(TAG, "onMapReady - SingleMarkerMap")
        mMap = p0
        val markerPosition = LatLng(lat, long)
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(markerPosition, 15F))
        mMap.setOnCameraIdleListener {
            Log.d(TAG, "camera is idle")
            if (!markerDisplayed) {
                markerDisplayed = true
                mMap.addMarker(MarkerOptions().position(markerPosition).title(title))
                    ?.showInfoWindow()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        Log.d(TAG, "onCreateOptionsMenu - SingleMarkerMap")
        inflater.inflate(R.menu.main_menu, menu)
        // Set the click listener for menu items
        for (i in 0 until menu.size()) {
            val menuItem = menu.getItem(i)
            menuItem.setOnMenuItemClickListener(this)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        Log.d(TAG, "onMenuItemClick - SingleMarkerMap")
        when (item.itemId) {
            R.id.menuShare -> {
                Log.d(TAG, "menu share pressed")
                showShareScreen(mMap, requireActivity(), requireContext(), arrayListOf(title))
                return true
            }
            R.id.menuAddToCalendar -> {
                Log.d(TAG, "add to calendar pressed")
                addToCalendar(requireContext(), getString(R.string.icesatFlyover), dateObjectTime, lat, long)
                return true
            }
        }
        return false
    }
}
