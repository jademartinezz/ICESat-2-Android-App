package gov.nasa.gsfc.icesat2.icesat_2

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng

class MainViewModel : ViewModel() {
    companion object {
        private const val TAG = "MainViewModel"
    }
    var allPointsList = MutableLiveData<ArrayList<Point>>()

    fun getAllPointsList(): LiveData<ArrayList<Point>> {
        Log.d(TAG, "getAllPointsList - MainViewModel")
        return allPointsList
    }

    var pastPointsList = MutableLiveData<ArrayList<Point>>()

    var allPointsChain = MutableLiveData<ArrayList<ArrayList<Point>>>()

    fun getAllPointsChain(): LiveData<ArrayList<ArrayList<Point>>> {
        Log.d(TAG, "getAllPointsChain - MainViewModel")
        return allPointsChain
    }

    var searchCenter = MutableLiveData<LatLng>()

    fun getSearchCenter(): LiveData<LatLng> {
        Log.d(TAG, "getSearchCenter - MainViewModel")
        return searchCenter
    }

    val searchRadius = MutableLiveData<Double>()

    fun getSearchRadius(): LiveData<Double> {
        Log.d(TAG, "getSearchRadius - MainViewModel")
        return searchRadius
    }

    val searchString = MutableLiveData<String>()

    fun getSearchString(): LiveData<String> {
        Log.d(TAG, "getSearchString - MainViewModel")
        return searchString
    }

    val notificationTime = MutableLiveData<Long>()

    var trackingData = MutableLiveData<ArrayList<TrackingPoint>>()

    fun getTrackingData(): LiveData<ArrayList<TrackingPoint>> {
        Log.d(TAG, "getTrackingData - MainViewModel")
        return trackingData
    }
}