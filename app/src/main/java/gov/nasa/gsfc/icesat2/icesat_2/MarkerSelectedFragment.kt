package gov.nasa.gsfc.icesat2.icesat_2

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
// import androidx.lifecycle.ViewModelProviders // DEPRECATED, REPLACED WITH BELOW LINE
import androidx.fragment.app.viewModels
import gov.nasa.gsfc.icesat2.icesat_2.favoritesdb.FavoritesEntry
import gov.nasa.gsfc.icesat2.icesat_2.ui.favorites.FavoritesViewModel
// import kotlinx.android.synthetic.main.fragment_marker_selected.*m // DEPRECATED LANGUAGE
import java.util.*


private const val TAG = "MarkerSelectedFragment"
private const val ARG_PARAM3 = "param3"
private const val ARG_PARAM4 = "param4"

class MarkerSelectedFragment : Fragment(), IGeocoding, ITimePickerCallback {

    private lateinit var textViewDate: TextView
    private lateinit var btnFavorite: ImageButton
    private lateinit var btnNotify: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var textViewTime: TextView
    private val favoritesViewModel: FavoritesViewModel by viewModels()
    private lateinit var selectedPoint: Point
    //one of these two will always be null because this is for one favorite entry and cannot both add and remove it
    private var favoritesEntryToAdd: Point? = null
    private var favoritesEntryToRemove: Point? = null
    private lateinit var geocoder: Geocoder
    private var isMarker: Boolean = true
    private lateinit var notifySharedPref: NotificationsSharedPref
    private lateinit var alarmManager: AlarmManager

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "savedInstanceState - MarkerSelectedFragment")
        super.onCreate(savedInstanceState)
        Log.d(TAG, "savedInstanceState - MarkerSelectedFragment")
        val args = requireArguments()
        selectedPoint = args.getParcelable(ARG_PARAM3, Point::class.java)
            ?: throw IllegalArgumentException("Argument ARG_PARAM3 must be provided and must be of type Point")
        isMarker = args.getBoolean(ARG_PARAM4)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView - MarkerSelectedFragment")
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_marker_selected, container, false)
        textViewDate = view.findViewById(R.id.textViewDate)
        textViewTime = view.findViewById(R.id.textViewTime)
        btnFavorite = view.findViewById(R.id.btnFavorite)
        btnNotify = view.findViewById(R.id.btnNotify)
        btnClose = view.findViewById(R.id.btnClose)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "START onViewCreated - MarkerSelectedFragment")
        super.onViewCreated(view, savedInstanceState)
        geocoder = context?.let { Geocoder(it) }!!
        notifySharedPref = NotificationsSharedPref(requireContext())
        alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (isMarker) {
            textViewDate.text = getString(R.string.dateDisplay, selectedPoint.dayOfWeek, selectedPoint.date, selectedPoint.year)
            textViewTime.text = getString(R.string.timeDisplay, selectedPoint.time, selectedPoint.ampm, selectedPoint.timezone)

            if (entryInDatabase(selectedPoint.dateObject.time)) {
                btnFavorite.setImageResource(R.drawable.ic_shaded_star_24)
                btnFavorite.tag = "favorite"
            }
            btnFavorite.setOnClickListener {
                if (btnFavorite.tag == "favorite") {
                    //remove from favorites
                    btnFavorite.setImageResource(R.drawable.ic_star_border_black_24dp)
                    btnFavorite.tag = "notFavorite"
                    Toast.makeText(requireContext(), "Removed From Favorites", Toast.LENGTH_SHORT)
                        .show()
                    favoritesEntryToAdd = null
                    favoritesEntryToRemove = selectedPoint
                } else {
                    btnFavorite.setImageResource(R.drawable.ic_shaded_star_24)
                    btnFavorite.tag = "favorite"
                    Toast.makeText(requireContext(), "Added to Favorites", Toast.LENGTH_SHORT)
                        .show()
                    favoritesEntryToAdd = selectedPoint
                    favoritesEntryToRemove = null
                }
            }

            val selectedPointTime = selectedPoint.dateObject.time
            if (notifySharedPref.contains("${selectedPointTime}_1") || notifySharedPref.contains("${selectedPointTime}_24") || notifySharedPref.contains("${selectedPointTime}_C")) {
                btnNotify.setImageResource(R.drawable.ic_baseline_notifications_active_24)
            }

            Log.d(TAG, "OnActivity Created notifications are")
            notifySharedPref.printAll()
            btnNotify.setOnClickListener {
                Log.d(TAG, "notify button clicked")
                //create dialog
                if (notifySharedPref.contains("${selectedPointTime}_1") || notifySharedPref.contains("${selectedPointTime}_24") || notifySharedPref.contains("${selectedPointTime}_C")) {
                    //removing notifications
                    btnNotify.setImageResource(R.drawable.ic_baseline_notifications_none_24)
                    deleteNotificationFromSPAndAlarmManager(arrayOf("$selectedPointTime" + "_24", "$selectedPointTime" + "_1", "${selectedPointTime}_C")) //the keys of the 24hr, 1hr, and custom alarm
                    Toast.makeText(context, "Notifications Removed", Toast.LENGTH_SHORT).show()
                } else {
                    val notificationsDialog = NotificationsDialog()
                    notificationsDialog.setListener(this) //when the user presses ok on the dialog will call notificationsOptionsChosen
                    notificationsDialog.show(childFragmentManager, "hello world")
                }
            }

        } else {
            //just want to display a chain entry
            textViewDate.text = getString(R.string.trackBeginsAt, selectedPoint.date, selectedPoint.year)
            textViewTime.visibility = View.GONE
            btnFavorite.visibility= View.INVISIBLE
            btnNotify.visibility = View.INVISIBLE
        }

        btnClose.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .remove(this@MarkerSelectedFragment)
                .commit()
        }

    }

    fun notificationOptionsChosen(arr: ArrayList<Int>) {
        Log.d(TAG, "notificationOptionsChosen - MarkerSelectedFragment")
        btnNotify.setImageResource(R.drawable.ic_baseline_notifications_active_24)
        // 0 -> 1 hrs; 1 -> 24hrs; 2 -> set custom
        val flyoverTime = selectedPoint.dateObject.time
        val baseTimeKey = flyoverTime.toString()
        for (element in arr) {
            var key = baseTimeKey
            Log.d(TAG, "Element: $element")
            when (element) {
                0 -> {
                    key += "_1"
                    //createAlarm(currentTime + 100000, key)
                    Log.d(TAG, "Create 1 hr alarm with key $key")
                    createAlarm(flyoverTime - 60 * 60 * 1000, key)
                }
                1 -> {
                    key += "_24"
                    Log.d(TAG, "Create24hr alarm with key $key")
                    //createAlarm(currentTime + 90000, key)
                    createAlarm(flyoverTime - 24 * 60 * 60 * 1000, key)
                }
                2 -> {
                    key += "_C"
                    Log.d(TAG, "Create custom alarm with key $key")
                    //launch the date picker
                    val calendar = getCalendarForSelectedPoint()
                    //calendar.timeZone = TimeZone.getTimeZone("UTC")
                    val datePickerFragment = DatePickerFragment(requireActivity())
                    datePickerFragment.setListener(this, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
                    datePickerFragment.show(childFragmentManager, "DatePicker")
                }
            }
        }
        notifySharedPref.printAll()
        if (!arr.contains(2) && arr.contains(0) && arr.contains(1)) {
            Toast.makeText(requireContext(), R.string.notificationSetMultiple, Toast.LENGTH_SHORT).show()
        } else if (!arr.contains(2) && (arr.contains(0) || arr.contains(1))) {
            Toast.makeText(requireContext(), R.string.notificationSet, Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteNotificationFromSPAndAlarmManager(arraySelectedPointTime: Array<String>) {
        Log.d(TAG, "deleteNotificationFromSPAndAlarmManager - MarkerSelectedFragment")
        Log.d(TAG, "Delete SP size of array is ${arraySelectedPointTime.size}")
        //remove the notification from storage in SharedPreferences
        for (string in arraySelectedPointTime) {
            notifySharedPref.delete(string)
            //actually cancel the alarm
            //create a pending intent with the same properties
            Log.d(TAG, "Attempting to cancel a pending intent $string")
            val intent = Intent(requireContext(), NotificationBroadcast::class.java)
            val pendingIntent = PendingIntent.getBroadcast(requireContext(), string.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE)
            pendingIntent.cancel()
            alarmManager.cancel(pendingIntent)
        }
        Log.d(TAG, "after deleting resulting notifications are")
        notifySharedPref.printAll()

    }

    private fun getCalendarForSelectedPoint(): Calendar {
        Log.d(TAG, "getCalendarForSelectedPoint - MarkerSelectedFragment")
        val selectedPointTime = selectedPoint.dateObject.time
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = selectedPointTime
        return calendar
    }

    override fun datePicked(year: Int, month: Int, day: Int) {
        Log.d(TAG, "datePicked - MarkerSelectedFragment")
        val calendar = getCalendarForSelectedPoint()
        calendar.timeZone = TimeZone.getDefault()

        //launch the timer picker
        val timePickerFragment = TimePickerFragment(requireActivity())
        timePickerFragment.setListener(this, year, month, day, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
        timePickerFragment.show(childFragmentManager, "My Message")
    }

    /**
     * Called once a time has been picked. Create an alarm to go off at the chosen time
     */
    override fun timePicked(year: Int, month: Int, day: Int, hour: Int, minute: Int) {
        Log.d(TAG, "timePicked - MarkerSelectedFragment")
        val calendar = Calendar.getInstance()
        calendar.set(year, month, day, hour, minute)
        //calendar.timeZone = TimeZone.getTimeZone("UTC")
        val time = calendar.timeInMillis
        val key = "${selectedPoint.dateObject.time}_C"
        createAlarm(time, key)
        Log.d(TAG, "Creating Custom alarm with key $key")
        Toast.makeText(requireContext(), R.string.notificationSet, Toast.LENGTH_SHORT).show()
    }

    /**
     * Does two things
     * 1) creates a pendingIntent to call [NotificationBroadcast] when alarm is triggered with, notification
     * specific extras (lat, long, time...etc)
     * 2) stores the details of the alarm in sharedPreferences, so the alarm can be recreated after device turns back on
     * Alarms are stored in shared preferences using the following format
     * key: timeOfFlyover; value: timeForAlarm, lat, long, timeString, searchString, dateString
     *
     * @param timeForAlarm when the alarm will go off
     * @param timeForKey the time of the flyover (can be the same, but almost always timeForAlarm will be first)
     */
    private fun createAlarm(timeForAlarm: Long, timeForKey: String) {
        Log.d(TAG, "createAlarm - MarkerSelectedFragment")
        val intent = Intent(requireContext(), NotificationBroadcast::class.java)

        //keeps the alarms unique
        intent.addCategory(timeForKey)

        val latLngString = "${selectedPoint.latitude}, ${selectedPoint.longitude}"
        val timeString = "${selectedPoint.time.substring(0,5)} ${selectedPoint.ampm} ${selectedPoint.timezone}"
        val searchString = MainActivity.getMainViewModel()?.searchString?.value
        val dateString = selectedPoint.date
        val hours = timeForKey.split("_")[1]
        //add the values as extras to the intent
        Log.d(TAG, "Adding timeForKey $timeForKey to intent")
        intent.putExtra(INTENT_FLYOVER_TIME_KEY, timeForKey) //flyoverTime key
        intent.putExtra(INTENT_LAT_LNG_STRING, latLngString)
        intent.putExtra(INTENT_TIME_STRING, timeString)
        intent.putExtra(INTENT_SEARCH_STRING, searchString)
        intent.putExtra(INTENT_DATE_STRING, dateString)
        intent.putExtra(INTENT_HOURS_REMINDER, hours)
        Log.d(TAG, "hashcode is ${timeForKey.hashCode()}")
        val pendingIntent = PendingIntent.getBroadcast(requireContext(), timeForKey.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE)

        //1) add to the list of alarms with a formattedString of format timeStampOfAlarm, lat, long, timeString, searchString, dateString
        notifySharedPref.addToNotificationSharedPref(timeForKey, "$timeForAlarm, $latLngString, $timeString, $searchString, $dateString")
        //2) set the alarm. Alarms tend to run a little late, so show them 1 minute (60000ms) before
        Log.d(TAG, "alarm set to go off in ${(timeForAlarm - System.currentTimeMillis()) / 1000}s")
        alarmManager.set(AlarmManager.RTC_WAKEUP, timeForAlarm, pendingIntent)
    }


    companion object {
        @JvmStatic
        fun newInstance(param3: Point, isMarker: Boolean) =
            MarkerSelectedFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PARAM3, param3)
                    putBoolean(ARG_PARAM4, isMarker)
                }
            }
    }

    /**
     * If the users starred a location, add it to favorites when this fragment gets closed
     */
    override fun onStop() {
        Log.d(TAG, "START onStop - MarkerSelectedFragment")
        super.onStop()
        if (favoritesEntryToAdd != null) {
            val geocodedString = getGeographicInfo(geocoder, selectedPoint.latitude, selectedPoint.longitude)
            val addingFavorite = FavoritesEntry(selectedPoint.dateObject.time, selectedPoint.dateString, selectedPoint.latitude, selectedPoint.longitude, geocodedString)
            if (!entryInDatabase(addingFavorite)) {
                Log.d(TAG, "entry is NOT in favorites. Adding it")
                favoritesViewModel.insert(addingFavorite)
            }
        } else if (favoritesEntryToRemove != null) {
            Log.d(TAG, "Entry was previously entered in favorites. Now removing it")
            favoritesViewModel.delete(selectedPoint.dateObject.time)
        }
        Log.d(TAG, "END onStop - MarkerSelectedFragment")
    }

    private fun entryInDatabase(favEntry: FavoritesEntry) : Boolean {
        Log.d(TAG, "entryInDatabase - MarkerSelectedFragment")
        return favoritesViewModel.contains(favEntry.dateObjectTime)
    }

    private fun entryInDatabase(dateTime: Long) : Boolean {
        Log.d(TAG, "entryInDatabase - MarkerSelectedFragment")
        return favoritesViewModel.contains(dateTime)
    }
}