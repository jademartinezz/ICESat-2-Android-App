package gov.nasa.gsfc.icesat2.icesat_2

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import gov.nasa.gsfc.icesat2.icesat_2.databinding.FragmentListBinding

private const val TAG = "ListFragment"

class ListFragment : Fragment(), ILaunchSingleMarkerMap {
    private lateinit var listener: ILaunchSingleMarkerMap
    private lateinit var recyclerView: RecyclerView  // Declare RecyclerView variable

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "START onCreateView - ListFragment.kt")
        // Inflate the layout for this fragment using view binding
        val binding = FragmentListBinding.inflate(inflater, container, false)
        recyclerView = binding.recyclerView  // Initialize RecyclerView using binding
        Log.d(TAG, "END onCreateView - ListFragment.kt")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "onViewCreated - ListFragment.kt")
        super.onViewCreated(view, savedInstanceState)
        val allPointsOneList = ArrayList<Point>()

        // Retrieve data from ViewModel and populate allPointsOneList
        if (MainActivity.getMainViewModel()?.pastPointsList?.value != null) {
            allPointsOneList.addAll(MainActivity.getMainViewModel()?.pastPointsList?.value!!)
        }

        if (MainActivity.getMainViewModel()?.allPointsList?.value != null) {
            allPointsOneList.addAll(MainActivity.getMainViewModel()?.allPointsList?.value!!)
        }

        Log.d(TAG, "size of allPointsList is ${allPointsOneList.size}.")
        setUpRecyclerView(allPointsOneList)
    }

    private fun setUpRecyclerView(allPointsOneList: ArrayList<Point>?) {
        Log.d(TAG, "setUpRecyclerView - ListFragment.kt")
        val listRecyclerViewAdapter = ListRecyclerViewAdapter(requireContext(), allPointsOneList!!)
        listRecyclerViewAdapter.setUpListener(this)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = listRecyclerViewAdapter
    }

    fun setUpListener(listener: ILaunchSingleMarkerMap) {
        this.listener = listener
    }

    override fun navigateToSingleMarkerMap(lat: Double, long: Double, title: String, dateObjectTime: Long) {
        Log.d(TAG, "START navigateToSingleMarkerMap - ListFragment.kt")
        listener.navigateToSingleMarkerMap(lat, long, title, dateObjectTime)
        Log.d(TAG, "END navigateToSingleMarkerMap - ListFragment.kt")
    }
}
