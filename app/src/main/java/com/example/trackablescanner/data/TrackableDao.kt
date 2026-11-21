package com.example.trackablescanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackableDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(trackable: TrackableEntity): Long

    @Query("SELECT * FROM trackables ORDER BY addedAt DESC")
    fun getAll(): Flow<List<TrackableEntity>>

    @Query("DELETE FROM trackables WHERE code = :code")
    suspend fun deleteByCode(code: String)

    @Query("DELETE FROM trackables")
    suspend fun deleteAll()
}