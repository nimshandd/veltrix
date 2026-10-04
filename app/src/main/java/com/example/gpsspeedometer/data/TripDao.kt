package com.example.gpsspeedometer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startedAt DESC")
    fun observeTrips(): Flow<List<Trip>>

    @Insert
    suspend fun insert(trip: Trip)

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteById(tripId: Long)

    @Query("DELETE FROM trips")
    suspend fun deleteAll()
}
