package gov.nasa.gsfc.icesat2.icesat_2.ui.search

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.snackbar.Snackbar
import gov.nasa.gsfc.icesat2.icesat_2.DEFAULT_SEARCH_RADIUS
import gov.nasa.gsfc.icesat2.icesat_2.R
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

private const val TAG = "SearchFragment"

class SearchFragment : Fragment() {

    private lateinit var listener: ISearchFragmentCallback
    private var address: String? = null
    private var simpleSearch = true

    // Declare views
    private lateinit var textViewAdvancedSearch: View
    private lateinit var textViewSimpleSearch: View
    private lateinit var editTextLat: EditText
    private lateinit var editTextLon: EditText
    private lateinit var unitSpinner: Spinner
    private lateinit var editTextRadius: EditText
    private lateinit var btnSearch: Button
    private lateinit var checkBoxPast: CheckBox
    private lateinit var checkBoxFuture: CheckBox
    private lateinit var btnUseCurrentLoc: Button
    private lateinit var btnSelectOnMap: Button
    private lateinit var btnUseSearchBar: Button
    private lateinit var btnTrack: Button
    private lateinit var textViewEnterLocation: TextView
    private lateinit var textViewAddress: TextView
    private lateinit var searchFragSnackCoordinator: View
    private lateinit var autocompleteLauncher: ActivityResultLauncher<Intent>

    private var navController: NavController? = null // Use nullable NavController

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "START/END onCreateView - SearchFragment")
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "START onViewCreated - SearchFragment")

        navController = Navigation.findNavController(view)

        // Initialize views and cast them
        textViewAdvancedSearch = view.findViewById<TextView>(R.id.textViewAdvancedSearch)!!
        textViewSimpleSearch = view.findViewById<TextView>(R.id.textViewSimpleSearch)!!
        view.findViewById<EditText>(R.id.editTextLat)!!.also { editTextLat = it }
        view.findViewById<EditText>(R.id.editTextLon)!!.also { editTextLon = it }
        view.findViewById<Spinner>(R.id.unitSpinner)!!.also { unitSpinner = it }
        view.findViewById<EditText>(R.id.editTextRadius)!!.also { editTextRadius = it }
        view.findViewById<Button>(R.id.btnSearch)!!.also { btnSearch = it }
        view.findViewById<Button>(R.id.btnUseCurrentLoc)!!.also { btnUseCurrentLoc = it }
        view.findViewById<Button>(R.id.btnSelectOnMap)!!.also { btnSelectOnMap = it }
        view.findViewById<Button>(R.id.btnUseSearchBar)!!.also { btnUseSearchBar = it }
        view.findViewById<Button>(R.id.btnTrack)!!.also { btnTrack = it }
        view.findViewById<CheckBox>(R.id.checkBoxPast)!!.also { checkBoxPast = it }
        view.findViewById<CheckBox>(R.id.checkBoxFuture)!!.also { checkBoxFuture = it }
        textViewEnterLocation = view.findViewById(R.id.textViewEnterLocation)!!
       // textViewAddress = view.findViewById(R.id.textViewAddress)!!
        searchFragSnackCoordinator = view.findViewById(R.id.searchFragSnackCoordinator)
        setHasOptionsMenu(true)

        // Initialize the launcher in onViewCreated
        autocompleteLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val intent = result.data
                intent?.let { handleAutocompleteResult(it) }
            } else if (result.resultCode == AutocompleteActivity.RESULT_ERROR) {
                val status = Autocomplete.getStatusFromIntent(result.data!!)
                Log.i(TAG, "Error: ${status.statusMessage}")
            }
        }
        // Example assuming btnSelectOnMap is a Button
        val btnSelectOnMap = view.findViewById<Button>(R.id.btnSelectOnMap)
        btnSelectOnMap.setOnClickListener {
            navController?.navigate(R.id.action_navigation_home_to_mapFragment2)
        }
        Log.d(TAG, "END onViewCreated - SearchFragment")
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onResume() {
        Log.d(TAG, "START onResume - SearchFragment")
        super.onResume()
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Register the listener to be the parent activity every time the fragment is resumed.
        listener = requireActivity() as ISearchFragmentCallback

        // Set up click listeners
        setupClickListeners()

        // Set up the spinner
        setupSpinner()

        // Initialize dependencies and such to be able to use address searchbar
        Places.initialize(requireContext(), getString(R.string.google_maps_key))
        Places.createClient(requireContext())
        // Update hint based on Spinner selection
        unitSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                updateRadiusHint()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Optional: Handle the case where nothing is selected
            }
        }
        Log.d(TAG, "END onResume - SearchFragment")
    }
    private fun updateRadiusHint() {
        Log.d(TAG, "START updateRadiusHint - SearchFragment")
        val selectedUnit = unitSpinner.selectedItem.toString()
        val hintResId = when (selectedUnit) {
            "Miles" -> R.string.radius_mi
            "Kilometers" -> R.string.radius_km
            else -> R.string.radius_mi // Default fallback
        }
        editTextRadius.hint = getString(hintResId)
        Log.d(TAG, "END updateRadiusHint - SearchFragment")
    }

    private fun setupClickListeners() {
        Log.d(TAG, "START setupClickListeners - SearchFragment")
        simpleSearch = textViewAdvancedSearch.isVisible
        btnTrack.setOnClickListener {
            listener.trackButtonPressed()
        }

        btnSearch.setOnClickListener {
            val inputs = allInputsValid() // Returns array of {lat, long, radius} if valid. null if not valid
            if (inputs != null) {
                Log.d(TAG, "showing past results: ${if (checkBoxPast.visibility == View.VISIBLE) checkBoxPast.isChecked else false}")
                Log.d(TAG, "showing future results: ${if (checkBoxFuture.visibility == View.VISIBLE) checkBoxFuture.isChecked else false}")
                if (!checkBoxPast.isChecked && !checkBoxFuture.isChecked) {
                    createSnackbar(getString(R.string.searchCheckBoxes))
                    return@setOnClickListener
                }
                listener.searchButtonPressed(inputs[0], inputs[1], inputs[2], false, -1L, checkBoxPast.isChecked, checkBoxFuture.isChecked)
            }
        }

        btnUseCurrentLoc.setOnClickListener {
            listener.useCurrentLocationButtonPressed(simpleSearch)
        }

        btnSelectOnMap.setOnClickListener {
            listener.selectOnMapButtonPressed()
        }

        btnUseSearchBar.setOnClickListener {
            useSearchBar()
        }

        textViewAdvancedSearch.setOnClickListener {
            // Hide advanced search text + show advanced search fields
            simpleSearch = false
            textViewAdvancedSearch.visibility = View.GONE

            checkBoxPast.visibility = View.VISIBLE
            checkBoxFuture.visibility = View.VISIBLE
            textViewSimpleSearch.visibility = View.VISIBLE
            editTextLat.visibility = View.VISIBLE
            editTextLon.visibility = View.VISIBLE
            unitSpinner.visibility = View.VISIBLE
            editTextRadius.visibility = View.VISIBLE
            btnSearch.visibility = View.VISIBLE
        }

        textViewSimpleSearch.setOnClickListener {
            // Hide advanced search fields + show advanced search text
            simpleSearch = true
            textViewAdvancedSearch.visibility = View.VISIBLE
            textViewSimpleSearch.visibility = View.GONE

            checkBoxPast.visibility = View.GONE
            checkBoxFuture.visibility = View.GONE
            editTextLat.visibility = View.GONE
            editTextLon.visibility = View.GONE
            unitSpinner.visibility = View.GONE
            editTextRadius.visibility = View.GONE
            btnSearch.visibility = View.GONE
        }

        editTextLat.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                address = "custom"
               // setAddressTextView()
            }
        }

        editTextLon.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                address = "custom"
               // setAddressTextView()
            }
        }
        Log.d(TAG, "END setupClickListeners - SearchFragment")
    }

    private fun setupSpinner() {
        Log.d(TAG, "START setupSpinner - SearchFragment")
        val adapter = ArrayAdapter.createFromResource(requireContext(), R.array.unitSelector, android.R.layout.simple_spinner_dropdown_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        unitSpinner.adapter = adapter
        Log.d(TAG, "END setupSpinner - SearchFragment")
    }

    fun setLatLngTextViews(lat: String, long: String) {
        Log.d(TAG, "START setLatLngTextViews - SearchFragment")
        editTextLat.setText(lat)
        editTextLon.setText(long)
        Log.d(TAG, "END setLatLngTextViews - SearchFragment")
    }

    private fun setLatLngTextViews(value: LatLng?) {
        Log.d(TAG, "START setLatLngTextViews - SearchFragment")
        if (value != null) {
            setLatLngTextViews(value.latitude.toString(), value.longitude.toString())
        } else {
            Toast.makeText(requireContext(), "Error Occurred", Toast.LENGTH_LONG).show()
        }
        Log.d(TAG, "END setLatLngTextViews - SearchFragment")
    }

    private fun clearRadiusTextView() {
        Log.d(TAG, "START clearRadiusTextView - SearchFragment")
        editTextRadius.setText("")
        Log.d(TAG, "END clearRadiusTextView - SearchFragment")
    }

    fun setAddressValue(newValue: String) {
        Log.d(TAG, "START setAddressValue - SearchFragment")
        address = newValue
        //setAddressTextView()
        Log.d(TAG, "END setAddressValue - SearchFragment")
    }

    fun getAddressValue(): String? = address

    // Return null if there is an error with one of the inputs. Otherwise return array of {lat, lng, radius}
    // NOTE: RADIUS can be entered in kilometers but will be converted immediately into miles to make for seamless use
    private fun allInputsValid(): DoubleArray? {
        Log.d(TAG, "START allInputsValid - SearchFragment")
        // Lat range -86, 86; lon range -180 180
        if (editTextLat.text.toString() == "") {
            createSnackbar(getString(R.string.latInputError, 0x00B0.toChar()))
            return null
        }
        val lat = editTextLat.text.toString().toDouble()
        if (lat < - 86 || lat > 86) {
            createSnackbar(getString(R.string.latInputError, 0x00B0.toChar()))
            return null
        }

        if (editTextLon.text.toString() == "") {
            createSnackbar(getString(R.string.longInputError, 0x00B0.toChar()))
            return null
        }
        val long = editTextLon.text.toString().toDouble()
        if (long < -180 || long > 180) {
            createSnackbar(getString(R.string.longInputError, 0x00B0.toChar()))
            return null
        }

        val radiusSelection = unitSpinner.selectedItem.toString()
        // No radius entered
        if (radiusSelection == "Miles" && editTextRadius.text.toString() == "") {
            createSnackbar(getString(R.string.radiusInputErrorMiles))
            return null
        } else if (radiusSelection == "Kilometers" && editTextRadius.text.toString() == "") {
            createSnackbar(getString(R.string.radiusInputErrorKilometers))
            return null
        }

        // Invalid value for radius
        var radius = editTextRadius.text.toString().toDouble()
        if (radiusSelection == "Miles" && (radius < 1.1 || radius > 25)) {
            createSnackbar(getString(R.string.radiusInputErrorMiles))
            return null
        } else if (radiusSelection == "Kilometers" && (radius < 1.8 || radius > 40.2)) {
            createSnackbar(getString(R.string.radiusInputErrorKilometers))
            return null
        }

        // Valid radius in kilometers -> convert it to miles
        if (radiusSelection == "Kilometers") {
            val kiloToMiles = 0.621371
            radius *= kiloToMiles
        }
        Log.d(TAG, "END allInputsValid - SearchFragment")
        return doubleArrayOf(lat, long, radius)
    }

    private fun createSnackbar(text: String) {
        Log.d(TAG, "START createSnackbar - SearchFragment")
        searchFragSnackCoordinator.let {
            Snackbar.make(it, text, Snackbar.LENGTH_LONG)
                .setAction(R.string.ok) { }
                .show()
        }
        Log.d(TAG, "END createSnackbar - SearchFragment")
    }

    @Deprecated("Deprecated in Java", ReplaceWith(
        "inflater.inflate(R.menu.search_frag_menu, menu)",
        "gov.nasa.gsfc.icesat2.icesat_2.R"
    )
    )
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        Log.d(TAG, "START onCreateOptionsMenu - SearchFragment")
        inflater.inflate(R.menu.search_frag_menu, menu)
        Log.d(TAG, "END onCreateOptionsMenu - SearchFragment")
        // super.onCreateOptionsMenu(menu, inflater) // DEPRECATED LANGUAGE
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "START onOptionsItemSelected - SearchFragment")
        when (item.itemId) {
            R.id.menuClearSearch -> {
                address = ""
                //setAddressTextView()
                setLatLngTextViews("", "")
                clearRadiusTextView()
                checkBoxPast.isChecked = false
                checkBoxFuture.isChecked = true
            }

            R.id.menuSearch -> {
                Log.d(TAG, "Search menu button pressed")
                useSearchBar()
            }
        }
        Log.d(TAG, "END onOptionsItemSelected - SearchFragment")
        return true
        // return super.onOptionsItemSelected(item) // DEPRECATED LANGUAGE
    }

    private fun handleAutocompleteResult(data: Intent) {
        Log.d(TAG, "START handleAutocompleteResult - SearchFragment")
        val place = Autocomplete.getPlaceFromIntent(data)
        Log.i(TAG, "Place: ${place.name}, simple search: $simpleSearch")
        val latLng = place.latLng
        setLatLngTextViews(latLng)
        address = place.name
        //setAddressTextView()
        if (place.latLng != null && simpleSearch) {
            listener.searchButtonPressed(place.latLng!!.latitude, place.latLng!!.longitude, DEFAULT_SEARCH_RADIUS, false)
        } else {
            editTextRadius.requestFocus()
        }
        Log.d(TAG, "END handleAutocompleteResult - SearchFragment")
    }

    private fun useSearchBar() {
        Log.d(TAG, "START useSearchBar - SearchFragment")
        // Set the fields to specify which types of place data to return after the user has made a selection.
        val fields = listOf(Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)

        // Start the autocomplete intent for the search.
        val intent = Autocomplete.IntentBuilder(
            AutocompleteActivityMode.FULLSCREEN, fields)
            .build(requireContext())

        // Use the launcher to start the activity for result
        autocompleteLauncher.launch(intent)
        Log.d(TAG, "END useSearchBar - SearchFragment")
    }

    override fun onDestroyView() {
        Log.d(TAG, "START onDestroyView - SearchFragment")
        super.onDestroyView()
        navController = null // Clear NavController reference to avoid memory leak
        Log.d(TAG, "END onDestroyView - SearchFragment")
    }
}