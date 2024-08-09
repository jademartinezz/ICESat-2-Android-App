package gov.nasa.gsfc.icesat2.icesat_2

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment


/**
 * Fragment whose ONLY use is to help with animation for [MarkerSelectedFragment]
 */
class DummyFragment : Fragment() {
    companion object {
        private const val TAG = "MainActivity"
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView - DummyFragment")
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_dummy, container, false)
    }

}