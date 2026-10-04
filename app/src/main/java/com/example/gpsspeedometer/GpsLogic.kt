package com.example.gpsspeedometer

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class DistanceSample(
    val latitude: Double,
    val longitude: Double,
    val timeMs: Long,
    val accuracyMeters: Float,
    val speedAccuracyMetersPerSecond: Float?
)

class SpeedFilter {
    private var previousSmoothedKmh: Double = 0.0

    fun reset() {
        previousSmoothedKmh = 0.0
    }

    fun update(rawSpeedKmh: Double, accuracyMeters: Double?, speedAccuracyMps: Double?): Double {
        val normalizedSpeed = rawSpeedKmh.coerceAtLeast(0.0)
        val reliability = computeReliability(accuracyMeters, speedAccuracyMps)
        val alpha = when {
            normalizedSpeed < 1.0 -> 0.16
            reliability > 0.8 -> 0.30
            reliability > 0.55 -> 0.22
            else -> 0.12
        }

        val smoothed = if (previousSmoothedKmh <= 0.0) {
            normalizedSpeed
        } else {
            previousSmoothedKmh + (normalizedSpeed - previousSmoothedKmh) * alpha
        }

        val finalSpeed = when {
            normalizedSpeed < 1.0 && smoothed < 1.0 -> 0.0
            else -> smoothed
        }
        previousSmoothedKmh = finalSpeed
        return finalSpeed
    }

    private fun computeReliability(accuracyMeters: Double?, speedAccuracyMps: Double?): Double {
        val accuracyScore = accuracyMeters?.let {
            val safeAccuracy = it.coerceIn(0.0, 50.0)
            (1.0 - safeAccuracy / 50.0).coerceIn(0.0, 1.0)
        } ?: 0.5

        val speedScore = speedAccuracyMps?.let {
            val safeSpeedAccuracy = it.coerceIn(0.0, 5.0)
            (1.0 - safeSpeedAccuracy / 5.0).coerceIn(0.0, 1.0)
        } ?: 0.5

        return ((accuracyScore + speedScore) / 2.0).coerceIn(0.0, 1.0)
    }
}

object DistanceValidator {
    fun isValidDelta(
        previous: DistanceSample,
        current: DistanceSample,
        elapsedMs: Long,
        gpsAccuracyMeters: Float,
        speedAccuracyMps: Float?
    ): Boolean {
        if (elapsedMs <= 0L || current.timeMs <= previous.timeMs) {
            return false
        }

        val deltaMeters = deltaMeters(previous, current)
        if (deltaMeters < 0.0) {
            return false
        }

        val maxPlausibleMeters = maxOf(
            25.0,
            (elapsedMs / 1000.0) * 30.0 + (gpsAccuracyMeters.coerceAtLeast(5f) + (speedAccuracyMps ?: 0f) * 10f + 10f)
        )

        return deltaMeters <= maxPlausibleMeters
    }

    fun deltaMeters(previous: DistanceSample, current: DistanceSample): Double {
        val lat1 = Math.toRadians(previous.latitude)
        val lon1 = Math.toRadians(previous.longitude)
        val lat2 = Math.toRadians(current.latitude)
        val lon2 = Math.toRadians(current.longitude)

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = sin(dLat / 2.0).pow(2.0) +
            cos(lat1) * cos(lat2) * sin(dLon / 2.0).pow(2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return 6_371_000.0 * c
    }
}
