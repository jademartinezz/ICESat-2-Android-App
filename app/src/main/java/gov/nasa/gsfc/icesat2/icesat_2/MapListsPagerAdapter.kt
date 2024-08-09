package gov.nasa.gsfc.icesat2.icesat_2

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter

private val TAB_TITLES = arrayOf(
    R.string.map,
    R.string.list
)

class MapListsPagerAdapter(
    fm: FragmentManager,
    lifecycle: Lifecycle,
    private val listener: ILaunchSingleMarkerMap
) : FragmentStateAdapter(fm, lifecycle) {
    companion object {
        const val TAG = "MapListsPagerAdapter"
    }
    override fun getItemCount(): Int {
        Log.d(TAG, "getItemCount - MapListsPagerAdapter")
        return TAB_TITLES.size
    }

    override fun createFragment(position: Int): Fragment {
        Log.d(TAG, "createFragment - MapListsPagerAdapter")
        return if (position == 0) {
            MapFragment()
        } else {
            val listFragment = ListFragment()
            listFragment.setUpListener(listener)
            listFragment
        }
    }

}
