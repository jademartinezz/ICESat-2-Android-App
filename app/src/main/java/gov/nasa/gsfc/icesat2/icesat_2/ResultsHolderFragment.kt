package gov.nasa.gsfc.icesat2.icesat_2

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class ResultsHolderFragment : Fragment(), ILaunchSingleMarkerMap {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabs: TabLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView - ResultsHolderFragment")
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_results_holder, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "onViewCreated - ResultsHolderFragment")
        viewPager = view.findViewById(R.id.viewPager)
        tabs = view.findViewById(R.id.tabs)

        val sectionsPagerAdapter = MapListsPagerAdapter(childFragmentManager, lifecycle, this)
        viewPager.adapter = sectionsPagerAdapter

        // Use TabLayoutMediator to connect TabLayout with ViewPager2
        TabLayoutMediator(tabs, viewPager) { tab, position ->
            tab.text = resources.getString(TAB_TITLES[position])
        }.attach()
    }

    override fun navigateToSingleMarkerMap(lat: Double, long: Double, title: String, dateObjectTime: Long) {
        Log.d(TAG, "START navigateToSingleMarkerMap - ResultsHolderFragment")
        Log.d(TAG, "Lat: $lat$")
        Log.d(TAG, "Long: $long")
        Log.d(TAG, "Title: $title")
        Log.d(TAG, "dateObjectTime: $dateObjectTime")
        val action = ResultsHolderFragmentDirections.actionResultsHolderFragmentToSingleMarkerMap(
            lat = lat.toFloat(), long = long.toFloat(), title = title, dateObjectTime = dateObjectTime
        )
        findNavController().navigate(action)
        Log.d(TAG, "END navigateToSingleMarkerMap - ResultsHolderFragment")
    }

    companion object {
        private const val TAG = "ResultsHolderFragment"
        private val TAB_TITLES = arrayOf(
            R.string.map,
            R.string.list
        )
    }
}

