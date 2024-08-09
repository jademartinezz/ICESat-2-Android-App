package gov.nasa.gsfc.icesat2.icesat_2.favoritesdb

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

private const val TAG = "FavoritesDatabase"

@Database(version = 2, entities = [FavoritesEntry::class], exportSchema = false)
abstract class FavoritesDatabase : RoomDatabase() {

    abstract fun favoritesDao(): FavoritesDao //all necessary code for this method generated at runtime

    companion object {
        @Volatile
        private var instance: FavoritesDatabase? = null

        fun getInstance(context: Context): FavoritesDatabase {
            Log.d(TAG, "getInstance - FavoritesDatabase")
            if (instance == null) {
                Log.d(TAG, "getInstance: instance is null")
                synchronized(FavoritesDatabase::class.java) {
                    if (instance == null) {
                        instance = Room.databaseBuilder(
                            context.applicationContext,
                            FavoritesDatabase::class.java, "favorites_table"
                        )
                            .fallbackToDestructiveMigration()
                            .build() //used builder because class is abstract
                    }
                }
            }
            return instance!!
        }
    }
}