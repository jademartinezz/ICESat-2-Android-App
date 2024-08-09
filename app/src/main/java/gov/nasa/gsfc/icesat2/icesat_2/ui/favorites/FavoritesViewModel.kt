package gov.nasa.gsfc.icesat2.icesat_2.ui.favorites

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import gov.nasa.gsfc.icesat2.icesat_2.FavoritesRepository
import gov.nasa.gsfc.icesat2.icesat_2.favoritesdb.FavoritesEntry

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "FavoritesViewModel"
    }
    private var repository: FavoritesRepository = FavoritesRepository(application)
    private var allFavoritesData: LiveData<List<FavoritesEntry>> = repository.getAllFavorites()

    fun insert(favoritesEntry: FavoritesEntry) {
        Log.d(TAG, "insert - FavoritesViewModel")
        repository.insert(favoritesEntry)
    }

    fun contains(timeEntry: Long): Boolean {
        Log.d(TAG, "contains - FavoritesViewModel")
        return repository.contains(timeEntry)
    }

    fun delete(timeEntry: Long) {
        Log.d(TAG, "delete - FavoritesViewModel")
        repository.delete(timeEntry)
    }

    fun deleteAll() {
        Log.d(TAG, "deleteAll - FavoritesViewModel")
        repository.deleteAllFavorites()
    }

    fun getAllFavorites(): LiveData<List<FavoritesEntry>> {
        Log.d(TAG, "getAllFavorites - FavoritesViewModel")
        return allFavoritesData
    }
}