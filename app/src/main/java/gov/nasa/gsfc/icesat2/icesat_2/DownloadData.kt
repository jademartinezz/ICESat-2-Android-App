package gov.nasa.gsfc.icesat2.icesat_2

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

private const val TAG = "DownloadData"
private const val DATE_ALREADY_PASSED = "DATE_ALREADY_PASSED"
private const val DATE_IN_FUTURE = "DATE_IN_FUTURE"
private const val JOB_TIMEOUT = 4000L

class DownloadData(private val url: URL, context: Context) {

    private lateinit var mainSearchJob: Job
    private var listener: IDownloadDataErrorCallback = context as MainActivity

    private val currentTime = Calendar.getInstance(TimeZone.getTimeZone("UTC")).time

    private val comparator = object : Comparator<Point> {
        /**
         * used for sorting the points downloaded based on the time of the flyover. Used when
         * Collections.sort() is called
         * @param o1 first point
         * @param o2 second
         * @return a negative value if o1 < o2 and a positive value if o1 > o2
         */
        override fun compare(o1: Point?, o2: Point?): Int {
            Log.d(TAG, "compare")
            if (o1 != null && o2 != null) {
                return o1.dateObject.compareTo(o2.dateObject)
            } else {
                Log.d(TAG, "Error in comparator method")
                throw IllegalArgumentException("Passed a null date into the comparator")
            }
        }
    }

    suspend fun startDownloadDataProcess(keepPastResults: Boolean, keepFutureResults: Boolean) : Boolean{
        Log.d(TAG, "START startDownloadDataProcess - DownloadData")
        Log.d(TAG, "Error in comparator method")
        var result = false
        withContext(Dispatchers.IO) {
            val job = withTimeoutOrNull(JOB_TIMEOUT) {
                result = startDownload(keepPastResults, keepFutureResults) // wait until job is done
                Log.d(TAG, "Download finished")
            }

            if(job == null){
                Log.d(TAG, "Canceling search job")
                mainSearchJob.cancel()

                listener.addErrorToSet(SearchError.TIMED_OUT)
            }
        }
        Log.d(TAG, "Start download process result is $result")
        Log.d(TAG, "END startDownloadDataProcess - DownloadData")
        return result
    }

    //return true if any points meet search criteria. False if no points meet criteria
    private suspend fun startDownload(keepPastResults: Boolean, keepFutureResults: Boolean): Boolean{
        Log.d(TAG, "START startDownload - DownloadData")
        var resultsFound = false
        mainSearchJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonText = url.readText()
                val jsonObject = JSONObject(jsonText)

                val state = jsonObject.getString("state")
                if (state == "true") {
                    val pointsArrayList = ArrayList<Point>()
                    val pastPointsArrayList = ArrayList<Point>()
                    val queryResult = jsonObject.getJSONArray("result")
                    for (i in 0 until queryResult.length()) {
                        val individualPoint = queryResult.getJSONObject(i)
                        val date = individualPoint.getString("date")
                        val time = individualPoint.getString("time")
                        val lon = individualPoint.getDouble("lon")
                        val lat = individualPoint.getDouble("lat")
                        val convertedDateTime: Deferred<Array<Any?>?> = async(Dispatchers.IO) {
                            convertDateTime(date, time, keepPastResults, keepFutureResults)
                        }

                        if (convertedDateTime.await() != null) {
                            //Wait until conversion is completed. Add the point only if it is in future
                            if (keepFutureResults && convertedDateTime.await()!![8] == DATE_IN_FUTURE) {
                                val newPoint = Point(
                                    convertedDateTime.await()!![0] as String, convertedDateTime.await()!![1] as String,
                                    convertedDateTime.await()!![2] as String, convertedDateTime.await()!![3] as String, convertedDateTime.await()!![4] as String,
                                    convertedDateTime.await()!![5] as String, convertedDateTime.await()!![6] as String, lon, lat, convertedDateTime.await()!![7] as Date)
                                pointsArrayList.add(newPoint)
                            } else if (keepPastResults && convertedDateTime.await()!![8] == DATE_ALREADY_PASSED) {
                                val oldPoint = Point(convertedDateTime.await()!![0] as String, convertedDateTime.await()!![1] as String,
                                    convertedDateTime.await()!![2] as String, convertedDateTime.await()!![3] as String, convertedDateTime.await()!![4] as String,
                                    convertedDateTime.await()!![5] as String, convertedDateTime.await()!![6] as String, lon, lat, convertedDateTime.await()!![7] as Date)
                                pastPointsArrayList.add(oldPoint)
                            }
                        }
                    }

                    val mainActivityViewModel = MainActivity.getMainViewModel()

                    //if there are any results from the search. Sort them and split them accordingly
                    if (pointsArrayList.size > 0 || pastPointsArrayList.size > 0) {
                        Log.d(TAG, "prior to sorting $pointsArrayList")
                        //sort the pointsArrayList based on date with earlier dates coming at the beginning
                        val sortPointArrayUnit: Deferred<Unit> = async {
                            Collections.sort(pointsArrayList, comparator)
                        }
                        sortPointArrayUnit.await()

                        Log.d(TAG, "After sorting $pointsArrayList")

                        //sort the past points
                        val sortPastPointArrayUnit: Deferred<Unit> = async {
                            Collections.sort(pastPointsArrayList, comparator)
                        }
                        sortPastPointArrayUnit.await()

                       /* val allPointChains: Deferred<ArrayList<ArrayList<Point>>> = async {
                            splitPointsByDate(pointsArrayList)
                        }*/

                        if (mainActivityViewModel != null) {
                            Log.d(TAG, "Posting all points list to viewmodel")
                            mainActivityViewModel.allPointsList.postValue(pointsArrayList)
                            resultsFound = true
                            //post the oldPoints too
                            mainActivityViewModel.pastPointsList.postValue(pastPointsArrayList)
                        }
                    } else {
                        //if we don't find any results post an empty list. Removes carryovers from displaying in searches that have no result
                        mainActivityViewModel?.allPointsChain?.postValue(ArrayList())

                        // No Results
                        listener.addErrorToSet(SearchError.NO_RESULTS)
                        resultsFound = false
                    }
                }

            } catch (e: Exception) {
                Log.d(TAG, "Exception in startDownload ${e.message}")
                e.printStackTrace()
                //listener.searchTimedOut()
            }
        }

        mainSearchJob.join()
        Log.d(TAG, "startDownload method ends. returning $resultsFound")
        Log.d(TAG, "END startDownload - DownloadData")
        return resultsFound
    }

    /**
     * Converts the downloaded time/data from UTC to users time zone.
     * @param dateString downloaded date of form 22-7-2020 (ie: July 22, 2020)
     * @param timeString time of the satellite flyover in UTC
     * @param keepPastResults whether we care about results that have already passed
     * If the date has already passed, will return arrayOf(DATE_ALREADY_PASSED, null) otherwise
     * @return arrayOf(convertedDateString, dayOfWeek, date, year, timeString, AM/PM, timezone, dateTimeToConvert, inPast/inFuture)
     */
    private fun convertDateTime(dateString: String, timeString: String, keepPastResults: Boolean, keepFutureResults: Boolean): Array<Any?>? {
        Log.d(TAG, "convert date and time")
        val inputFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val inputDate = "$dateString $timeString"
        val dateTimeToConvert = inputFormat.parse(inputDate)
        if (!keepPastResults && dateTimeToConvert!!.before(currentTime)) {
            return null
        }
        if (!keepFutureResults && !dateTimeToConvert!!.before(currentTime)) {
            return null
        }
        val outputFormat = SimpleDateFormat("EEE, MMM d, yyyy, hh:mm:ss, aaa, z", Locale.getDefault())
        outputFormat.timeZone = TimeZone.getDefault()
        val convertedDateString = outputFormat.format(dateTimeToConvert!!)

        //split up the date string
        val convertedDateStringSplit = convertedDateString.split(",")

        val alreadyPassed = if (dateTimeToConvert.before(currentTime)) {
            DATE_ALREADY_PASSED
        } else {
            DATE_IN_FUTURE
        }

        return arrayOf(convertedDateString, convertedDateStringSplit[0], convertedDateStringSplit[1].trimStart(),
            convertedDateStringSplit[2].trimStart(), convertedDateStringSplit[3].trimStart(), convertedDateStringSplit[4].trimStart(),
            convertedDateStringSplit[5].trimStart(), dateTimeToConvert, alreadyPassed
        )
    }


    fun downloadTrackingData(url: URL, listener: MainActivity) : ArrayList<TrackingPoint> {
        Log.d(TAG, "START downloadTrackingData - DownloadData")
        val trackingData = ArrayList<TrackingPoint>()
        try {
            Log.d(TAG, url.toString())
            val data = url.readText()
            Log.d(TAG, "Data is $data")

            val jsonArray = JSONArray(data)
            Log.d(TAG, "size is ${jsonArray.length()}")
            for (i in 0 until jsonArray.length()) {
                val currentObj = jsonArray.getJSONObject(i)
                val timeInMillis = currentObj.getLong("timeInMillis")
                val lat = currentObj.getDouble("lat")
                val lon = currentObj.getDouble("lon")
                val description = currentObj.getString("dateString")
                trackingData.add(TrackingPoint(timeInMillis, lat, lon, description))
            }
            Log.d(TAG, "trackingData \n $trackingData")
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "Downloading Tracking data exception ${e.message}")
            listener.showDialogOnMainThread(R.string.serverError, R.string.serverErrorDescription, R.string.ok)
        }
        Log.d(TAG, "START downloadTrackingData - DownloadData")
        return trackingData
    }
}

data class TrackingPoint(val timeInMillis:Long, val lat: Double, val long: Double, val description: String)