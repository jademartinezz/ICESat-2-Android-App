package gov.nasa.gsfc.icesat2.icesat_2

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import gov.nasa.gsfc.icesat2.icesat_2.favoritesdb.FavoritesEntry

private const val TAG = "FavoritesAdapter"

class FavoritesAdapter(
    private val context: Context,
    private val allFavorites: List<FavoritesEntry>
) : RecyclerView.Adapter<FavoritesAdapter.FavoritesHolder>() {

    private lateinit var listener: ILaunchSingleMarkerMap

    inner class FavoritesHolder(view: View) : RecyclerView.ViewHolder(view){
        var textViewDateTime: TextView = view.findViewById(R.id.textViewDateTime)
        var textViewLatLng: TextView = view.findViewById(R.id.textViewLatLng)
        var locationListLinearLayout: LinearLayout = view.findViewById(R.id.locationListLinearLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoritesHolder {
        Log.d(TAG, "onCreateViewHolder - FavoritesAdapter")
        val view = LayoutInflater.from(parent.context).inflate(R.layout.location_list_ticket, parent, false)
        return FavoritesHolder(view)
    }

    override fun onBindViewHolder(holder: FavoritesHolder, position: Int) {
        Log.d(TAG, "onBindViewHolder - FavoritesAdapter")
        val favorite = allFavorites[position]
        holder.textViewDateTime.text = favorite.dateString
        holder.textViewLatLng.text = context.getString(
            R.string.geoLatLng, favorite.geocodedLocation,
            favorite.lat.toString(), 0x00B0.toChar(), favorite.lng.toString(), 0x00B0.toChar())
        holder.locationListLinearLayout.setOnClickListener {

            val selectedFavorite = allFavorites[holder.absoluteAdapterPosition] // Use absoluteAdapterPosition
            Log.d(TAG, "onClick at Position ${holder.absoluteAdapterPosition}")
            Log.d(TAG, "data of point is ${allFavorites[holder.absoluteAdapterPosition].dateString}")

            // Callback inside of [FavoritesFragment] to launch going to the singleMarkerMap
            listener.navigateToSingleMarkerMap(selectedFavorite.lat, selectedFavorite.lng, selectedFavorite.dateString, selectedFavorite.dateObjectTime)
        }
    }

    override fun getItemCount(): Int {
        Log.d(TAG, "getItemCount - FavoritesAdapter")
        return allFavorites.size
    }

    fun setListener(listener: ILaunchSingleMarkerMap) {
        Log.d(TAG, "setListener - FavoritesAdapter")
        this.listener = listener
    }

}