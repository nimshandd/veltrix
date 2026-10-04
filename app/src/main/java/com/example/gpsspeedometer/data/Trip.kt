package com.example.gpsspeedometer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val distanceMeters: Double,
    val maxSpeedKmh: Double,
    val averageSpeedKmh: Double
)
