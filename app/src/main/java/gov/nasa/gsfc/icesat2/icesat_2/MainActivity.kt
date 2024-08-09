package gov.nasa.gsfc.icesat2.icesat_2

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import gov.nasa.gsfc.icesat2.icesat_2.databinding.ActivityMainNavBinding
import gov.nasa.gsfc.icesat2.icesat_2.ui.search.ISearchFragmentCallback
import gov.nasa.gsfc.icesat2.icesat_2.ui.search.SearchFragment
import kotlinx.coroutines.*
import java.net.URL
import kotlin.random.Random
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val DEFAULT_SEARCH_RADIUS = 25.0
private const val LOCATION_REQUEST_CODE = 6

class MainActivity : AppCompatActivity(), ISearchFragmentCallback, IDownloadDataErrorCallback, LocationListener {
    private lateinit var navController: NavController
    private var currentlySearching = false
    private var navHostFragment: Fragment? = null
    private lateinit var locationManager: LocationManager
    private var simpleSearch = true
    private var currentDestination: NavDestination? = null
    private var previousDestination: NavDestination? = null
    private var searchFragmentDestination: NavDestination? = null
    private val searchErrorSet = HashSet<SearchError>()
    private var waitingForLocation = false
    private lateinit var binding: ActivityMainNavBinding
    override lateinit var editTextLat: EditText
    override lateinit var editTextLon: EditText
    private var location: Location? = null  // Declare location variable

    companion object {
        private lateinit var mainViewModel: MainViewModel
        private const val TAG = "MainActivity"

        fun getMainViewModel(): MainViewModel? {
            Log.d(TAG, "START getMainViewModel - MainActivity")
            if (this::mainViewModel.isInitialized) {
                return mainViewModel
            }
            Log.d(TAG, "END getMainViewModel - MainActivity")
            return null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "START onCreate - MainActivity")
        val currentTimeInMillis = System.currentTimeMillis()
        Log.d(TAG, currentTimeInMillis.toString())

        super.onCreate(savedInstanceState)
        binding = ActivityMainNavBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Getting values for when activity launched by clicking on event
        val bundle = intent.extras
        val res = bundle?.getBoolean(NOTIFICATION_LAUNCHED_MAIN_ACTIVITY)
        val lat = intent.extras?.getDouble(NOTIFICATION_LAT)
        val long = bundle?.getDouble(NOTIFICATION_LONG)
        val time = bundle?.getLong(NOTIFICATION_TIME)
        // Initialize editTextLat and editTextLon from SearchFragment
        val fragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        if (fragment is NavHostFragment) {
            fragment.childFragmentManager.primaryNavigationFragment?.view?.let { view ->
                editTextLat = view.findViewById(R.id.editTextLat)
                editTextLon = view.findViewById(R.id.editTextLon)
            }
        }

        Log.d(TAG, "notification launched Main activity $res")
        Log.d(TAG, "lat is $lat; long is $long")

        //if launched from a notification
        if (res != null && res && lat != null && long != null && time != null) {
            searchButtonPressed(lat, long, DEFAULT_SEARCH_RADIUS, false, time)
        }

        // configure the bottom nav bar
        navController = findNavController(R.id.nav_host_fragment)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_search,
                R.id.navigation_favorites,
                R.id.navigation_gallery,
                R.id.navigation_info
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.bottomNavView.setupWithNavController(navController)

        //monitoring destinations so that if the user searches, goes somewhere else, and then comes back the search is still displayed
        currentDestination = navController.currentDestination


        navController.addOnDestinationChangedListener { _, destination, _ -> //maintain track of where we were in the search
            previousDestination = currentDestination
            currentDestination = destination

            if (destination.label == "Home" && searchFragmentDestination?.label == "Search Results" && previousDestination?.label != "Search Results" && previousDestination?.label != "Select Location On Map") {
                Log.d(TAG, "at search and searchFrag destination is Search Results")
                launchMapNoAnimation()
            } else if (destination.label == "Home" || destination.label == "Search Results") {
                Log.d(TAG, "searchFrag: setting searchFrag destination")
                searchFragmentDestination = destination
            }

            Log.d(TAG, "searchFrag destination is ${searchFragmentDestination?.label} \n previous destination is ${previousDestination?.label}")
        }

        navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Request location updates if permissions are granted
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request location permissions if not granted
            requestLocationPermissionDialog()
        } else {
            // Permissions are granted, initialize locationManager and request location updates
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                100,
                50F,
                this  // Register MainActivity as LocationListener
            )
        }

        Log.d(TAG, "END onCreate - MainActivity")
    }

    override fun onResume() {
        Log.d(TAG, "START onResume - MainActivity")
        super.onResume()
        waitingForLocation = false
        Log.d(TAG, "END onResume - MainActivity")
    }

    /**
     * Starts the searching
     * @param lat the latitude to search for
     * @param long the longitude to search for
     * @param radius the radius of the search (always in miles. If entered in km it was converted)
     * @param calledFromSelectOnMap whether the search was started by the user choosing a location on the map
     * Changes where the user navigates from so we need to keep track of it
     * @param time -1 unless called from the user clicking on a notification
     *
     */
    override fun searchButtonPressed(lat: Double, long: Double, radius: Double, calledFromSelectOnMap: Boolean, time: Long,
                                     pastResults: Boolean, futureResults: Boolean) {
        Log.d(TAG, "START searchButtonPressed - MainActivity")
        Log.d(TAG, "Time is $time")
        val serverLocation = "http://icesat2app-env.eba-gvaphfjp.us-east-1.elasticbeanstalk.com/find?lat=$lat&lon=$long&r=$radius&u=miles"
        Log.d(TAG, "MainActivity: starting download from $serverLocation")
        Log.d(TAG, "isNetworkConnected ${isNetworkConnected()}")
        var searchResultsFound = false

        /**
         * Determine if there are any results and store that in the searchFoundResults variable (true/ false)
         * Wait until that completes (jobDownloadData.join()) and if results found -> show them the results on Map
         * otherwise display a dialog that no results were found
         */
        if (!currentlySearching) {
            try {
                val url = URL(serverLocation)
                if (isNetworkConnected()) {
                    currentlySearching = true
                    CoroutineScope(Dispatchers.IO).launch {
                        val jobDownloadData = CoroutineScope(Dispatchers.IO).launch {
                            val downloadData = DownloadData(url, this@MainActivity)
                            val result: Deferred<Boolean> = async {
                                downloadData.startDownloadDataProcess(pastResults, futureResults)
                            }
                            searchResultsFound = result.await()
                        }
                        jobDownloadData.join()
                        Log.d(TAG, "searchResultsFound = $searchResultsFound")
                        if (searchResultsFound) {
                            Log.d(TAG, "YAY!! Search results found")
                            if (calledFromSelectOnMap) {
                                launchMapOnMainThread(lat, long, radius, R.id.action_selectOnMapFragment_to_resultsHolderFragment)
                            } else {
                                launchMapOnMainThread(lat, long, radius, R.id.action_navigation_search_to_resultsHolderFragment)
                            }
                        } else {
                            Log.d(TAG, "No search results found")
                            displayAppropriateDialog()

                        }
                        currentlySearching = false
                    }
                } else {
                    showDialog(R.string.noNetworkTitle, R.string.noNetworkDescription, R.string.ok)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Error in searching ${e.message}")
            }
        }
    }
    /**
     * Keeps track of errors that occurred during searching, so that they can be displayed to the user
     * after the search. Possible errors are of type [SearchError] and include TIMED_OUT and NO_RESULTS
     */
    override fun addErrorToSet(searchError: SearchError) {
        Log.d(TAG, "START/END addErrorToSet - MainActivity")
        Log.d(TAG, "addError method. Added $searchError")
        searchErrorSet.add(searchError)
    }
//    http://icesat2app-env.eba-gvaphfjp.us-east-1.elasticbeanstalk.com/find?lat=36.79864298773232&lon=-116.37995984405278&r=12.5&u=miles
//    http://icesat2app-env.eba-gvaphfjp.us-east-1.elasticbeanstalk.com/find?time=1722132165890&numResults=30/
//    http://icesat2apptracking-env.eba-maqwzufh.us-east-1.elasticbeanstalk.com/
    /**
     * At the end of the search, if there are errors display them and then clear the set. If there
     * are both TIMED_OUT and NO_RESULTS errors, just show the timed out message
     */
    private fun displayAppropriateDialog() {
        Log.d(TAG, "displayAppropriateDialog - MainActivity")
        Log.d(TAG, "displayAppropriateDialog starts with searchErrorSet $searchErrorSet")
        if (searchErrorSet.contains(SearchError.TIMED_OUT)) {
            CoroutineScope(Dispatchers.Main).launch {
                showDialog(R.string.searchError, R.string.searchErrorDescription, R.string.ok)
            }
        } else if (searchErrorSet.contains(SearchError.NO_RESULTS)) {
            CoroutineScope(Dispatchers.Main).launch {
                showDialogOnMainThread(R.string.noResults, R.string.noResultsDetails, R.string.backToSearch)
            }
        }
        searchErrorSet.clear()
        Log.d(TAG, "displayAppropriateDialog ends. searchErrorSet is $searchErrorSet")
    }

    /**
     * @return whether the user is connected to a network. This gets checked before starting a search
     */
    private fun isNetworkConnected(): Boolean {
        Log.d(TAG, "START isNetworkConnected - MainActivity")
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        Log.d(TAG, "END isNetworkConnected - MainActivity")
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    /**
     * Get the location of the user using a locationManager object.
     * @param simpleSearch If true starts the search immediately after getting the location, otherwise
     * fills the editText latitude and longitude boxes
     */
    override fun useCurrentLocationButtonPressed(simpleSearch: Boolean) {
        Log.d(TAG, "useCurrentLocationButtonPressed - MainActivity")
        this.simpleSearch = simpleSearch

        //check that the user has granted location permissions. If not, launch permission dialogs
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissionDialog()
            return
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        //check if the user's location is turned on
        if (!locationManager.isProviderEnabled("gps")) {
            showDialogOnMainThread(R.string.locationOffTitle, R.string.locationOffDescription, R.string.ok)
            return
        }

        //should always be true. Setting the "Searching for: " message
        val frag = getFrag()
        if (frag != null && frag is SearchFragment) {
            frag.setAddressValue(getString(R.string.yourLocation))
        }

        //the waitingForLocation variable used to so that the listener only gets set up once
        if (!waitingForLocation) {
            waitingForLocation = true
            locationManager.requestLocationUpdates("gps", 100, 50F, object : LocationListener {
                //when we get the location either start the searching or add it to the editTexts
                //when we get the location either start the searching or add it to the editTexts
                override fun onLocationChanged(p0: Location) {
                    Log.d(TAG, "START onLocationChanged - MainActivity")
                    location = p0  // Update location variable
                    updateEditTextWithLocation(p0.latitude.toString(), p0.longitude.toString())

                    if (frag is SearchFragment) {
                        frag.setLatLngTextViews(p0.latitude.toString(), p0.longitude.toString())
                        if (simpleSearch) {
                            searchButtonPressed(p0.latitude, p0.longitude, DEFAULT_SEARCH_RADIUS, false)
                        }
                        locationManager.removeUpdates(this)
                        waitingForLocation = false
                    }
                    if (simpleSearch) {
                        searchButtonPressed(p0.latitude, p0.longitude, DEFAULT_SEARCH_RADIUS, false)
                    }

                    locationManager.removeUpdates(this)
                    waitingForLocation = false
                }

                private fun updateEditTextWithLocation(lat: String, long: String) {
                    editTextLat.setText(lat)
                    editTextLon.setText(long)
                }

                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            })
        }
    }

    /**
     * @return the current fragment in the navController
     */
    private fun getFrag(): Fragment? {
        Log.d(TAG, "getFrag - MainActivity")
        return navHostFragment?.childFragmentManager?.fragments?.get(0)
    }

    /**
     * Called when user clicks on accept or deny from the location permission dialog. If the clicked allow,
     * call the useLocationButtonPressed method which will get passed the location checks this time
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Log.d(TAG, "onRequestPermissionsResult - MainActivity")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermission Callback")
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED && requestCode == LOCATION_REQUEST_CODE) {
            //clicked accept
            useCurrentLocationButtonPressed(simpleSearch)
        } else {
            //clicked deny
            Log.d(TAG, "Permission Denied in Callback")
            //if clicked 'Do Not ask again' on permissions dialog
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                grantAccessSnackbar()
            }
        }
    }
    /**
     * Creates the dialog box for asking for users location
     */
    private fun requestLocationPermissionDialog() {
        Log.d(TAG, "requestLocationPermissionDialog - MainActivity")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            LOCATION_REQUEST_CODE
        )
    }
    /**
     * Snack bar that shows up when location permissions have been permanently denied. Clicking on
     * Grant Access takes the user into settings to give the app permission to change their settings
     */
    private fun grantAccessSnackbar() {
        Log.d(TAG, "START grantAccessSnackbar - MainActivity")
        Snackbar.make(findViewById(R.id.constraintLayout), R.string.locationSnackbar, Snackbar.LENGTH_LONG)
            .setAction(R.string.grantAccess) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    intent.data = Uri.fromParts("package", this.packageName, null)
                    startActivity(intent)
            }
            .show()
        Log.d(TAG, "END grantAccessSnackbar - MainActivity")
    }

    /**
     * Navigate to [SelectOnMapFragment] or show dialog explaining that the user doesn't have a network connection
     */
    override fun selectOnMapButtonPressed() {
        Log.d(TAG, "START selectOnMapButtonPressed - MainActivity")
        if (isNetworkConnected()) {
            navController.navigate(R.id.selectOnMapFragment)
        } else {
            showDialog(R.string.noNetworkTitle, R.string.noNetworkDescription, R.string.ok)
        }
        Log.d(TAG, "END selectOnMapButtonPressed - MainActivity")
    }

    /**
     * If running on a background thread, switch back to the main thread
     * publish lat, long, radius, and searchString values to [MainViewModel]
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun launchMapOnMainThread(lat: Double, long: Double, radius: Double, navigationActionID: Int, time: Long = -1L) {
        GlobalScope.launch(Dispatchers.Main) {
            Log.d(TAG, "launchMapOnMainThread - MainActivity")
            showMap(navigationActionID)
            mainViewModel.notificationTime.value = time
            mainViewModel.searchCenter.value = LatLng(lat, long)
            mainViewModel.searchRadius.value = radius
            val frag = getFrag()
            if (frag != null && frag is SearchFragment) {
                mainViewModel.searchString.value = frag.getAddressValue()
            }
        }
    }

    /**
     * navigate to the map
     * @param navigationActionID the ID of the fragment we are navigating to
     */
    private fun showMap(navigationActionID: Int) {
        Log.d(TAG, "START showMap - MainActivity")
        //navController.navigate(R.id.action_navigation_home_to_mapFragment2)
        Log.d(TAG, "Show map called ${Random.nextInt(10)}")
        val frag = getFrag()
        if (frag is SearchFragment || frag is SelectOnMapFragment) {
            navController.navigate(navigationActionID)
        } else {
            Log.d(TAG, "NOT AN INSTANCE OF SEARCH FRAG :((--- ${Random.nextInt(10)}")
        }
        Log.d(TAG, "END showMap - MainActivity")
    }

    private fun launchMapNoAnimation() {
        Log.d(TAG, "START launchMapNoAnimation - MainActivity")
        navController.navigate(R.id.action_navigation_search_to_resultsHolderFragment, null, NavOptions.Builder().setEnterAnim(R.anim.blank_animation).setExitAnim(R.anim.blank_animation)
            .setPopEnterAnim(R.anim.slide_in_left).setPopExitAnim(R.anim.slide_out_right).build())
        Log.d(TAG, "END launchMapNoAnimation - MainActivity")
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun showDialogOnMainThread(title: Int, message: Int, buttonMessage: Int) {
        Log.d(TAG, "START showDialogOnMainThread - MainActivity")
        GlobalScope.launch(Dispatchers.Main) {
            showDialog(title, message, buttonMessage)
        }
        Log.d(TAG, "END showDialogOnMainThread - MainActivity")
    }

    private fun showDialog(title: Int, message: Int, buttonMessage: Int) {
        Log.d(TAG, "START showDialog - MainActivity")
        val alertBuilder = AlertDialog.Builder(this)
        alertBuilder.setMessage(message)
            ?.setTitle(title)
            ?.setPositiveButton(buttonMessage) { _, _ ->
                Log.d(TAG, "Dialog positive button clicked")
            }
        alertBuilder.show()
        Log.d(TAG, "END showDialog - MainActivity")
    }

    /**
     * when the arrow (back) button on the top action bar is clicked, do the normal backwards progression
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "onOptionsItemSelected - MainActivity")
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Download tracking data and then launch [SatelliteTrackingFragment]
     */
    override fun trackButtonPressed() {
        Log.d(TAG, " -------------------- START trackButtonPressed - MainActivity --------------------")
        if (!isNetworkConnected()) {
            showDialog(R.string.noNetworkTitle, R.string.noNetworkDescription, R.string.ok)
            return
        }

        val currentTimeInMillis = System.currentTimeMillis() - 1e11
//        val downloadLink = "http://icesat2apptracking-env.eba-maqwzufh.us-east-1.elasticbeanstalk.com/find?time=$currentTimeInMillis&numResults=30/"
        val  downloadLink = "http://icesat2apptracking-env.eba-maqwzufh.us-east-1.elasticbeanstalk.com/find?time=1.615999999999E12&numResults=30/"
        Log.d(TAG, downloadLink)
        try {
            val url = URL(downloadLink)
            val downloadData = DownloadData(url, this)
            CoroutineScope(Dispatchers.IO).launch {
                CoroutineScope(Dispatchers.IO).launch {
                    val trackingData: Deferred<ArrayList<TrackingPoint>> = async {
                        downloadData.downloadTrackingData(url, this@MainActivity)
                    }
                    if (trackingData.await().isNotEmpty()) {
                        Log.d(TAG, "Tracking data is NOT empty")
                        navigateToSatelliteTracking(trackingData.await())
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "url exception ${e.message}")
        }
        Log.d(TAG, "END trackButtonPressed - MainActivity")
    }

    private fun navigateToSatelliteTracking(trackingData: ArrayList<TrackingPoint>) {
        Log.d(TAG, "navigateToSatelliteTracking - MainActivity")
        CoroutineScope(Dispatchers.Main).launch {
            mainViewModel.trackingData.value = trackingData
            Log.d(TAG, "Posted Tracking data to view model")
            navController.navigate(R.id.action_navigation_search_to_satelliteTrackingFragment)
        }
    }

    // Override onSupportNavigateUp to handle navigation when using ActionBar
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onLocationChanged(p0: Location) {}

}
