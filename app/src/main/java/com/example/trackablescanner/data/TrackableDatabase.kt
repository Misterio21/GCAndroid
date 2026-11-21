package com.example.trackablescanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TrackableEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TrackableDatabase : RoomDatabase() {

    abstract fun trackableDao(): TrackableDao

    companion object {

        @Volatile
        private var INSTANCE: TrackableDatabase? = null

        fun getDatabase(context: Context): TrackableDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TrackableDatabase::class.java,
                    "trackables_database"
                ).build()

                INSTANCE = instance
                instance
            }
        }
    }
}