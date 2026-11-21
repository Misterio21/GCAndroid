package com.example.trackablescanner.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trackables",
    indices = [Index(value = ["code"], unique = true)]
)
data class TrackableEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val code: String,

    val addedAt: Long = System.currentTimeMillis()
)