package gov.nasa.gsfc.icesat2.icesat_2

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import gov.nasa.gsfc.icesat2.icesat_2.favoritesdb.FavoritesDao
import gov.nasa.gsfc.icesat2.icesat_2.favoritesdb.FavoritesDatabase
import gov.nasa.gsfc.icesat2.icesat_2.favoritesdb.FavoritesEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class FavoritesRepository(application: Application) {
    private var favoritesDao: FavoritesDao
    private var allFavoritesData: LiveData<List<FavoritesEntry>>

    companion object {
        private const val TAG = "MainActivity"
    }
    init {
        val database: FavoritesDatabase = FavoritesDatabase.getInstance(application)
        favoritesDao = database.favoritesDao()
        allFavoritesData = favoritesDao.getAllFavorites()
    }

    fun insert(favoritesEntry: FavoritesEntry) {
        Log.d(TAG, "insert - FavoritesRepository")
        CoroutineScope(Dispatchers.IO).launch {
            favoritesDao.insert(favoritesEntry)
        }
    }

    fun contains(timeKey: Long): Boolean = runBlocking(Dispatchers.IO) {
        Log.d(TAG, "contains - FavoritesRepository")
        favoritesDao.contains(timeKey).isNotEmpty()
    }

    fun delete(timeKey: Long) {
        Log.d(TAG, "delete - FavoritesRepository")
        CoroutineScope(Dispatchers.IO).launch {
            favoritesDao.delete(timeKey)
        }
    }

    fun deleteAllFavorites() {
        Log.d(TAG, "deleteAllFavorites - FavoritesRepository")
        CoroutineScope(Dispatchers.IO).launch {
            favoritesDao.deleteAllFavorites()
        }
    }

    fun getAllFavorites(): LiveData<List<FavoritesEntry>> {
        Log.d(TAG, "getAllFavorites - FavoritesRepository")
        return allFavoritesData
    }
}