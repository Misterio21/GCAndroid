package com.example.trackablescanner.data

import kotlinx.coroutines.flow.Flow

class TrackableRepository(
    private val dao: TrackableDao
) {

    val allTrackables: Flow<List<TrackableEntity>> = dao.getAll()

    suspend fun addTrackable(code: String): Boolean {
        val normalizedCode = code.trim().uppercase()

        if (normalizedCode.length != 6) {
            return false
        }

        val result = dao.insert(
            TrackableEntity(code = normalizedCode)
        )

        return result != -1L
    }

    suspend fun deleteTrackable(code: String) {
        dao.deleteByCode(code)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }
}