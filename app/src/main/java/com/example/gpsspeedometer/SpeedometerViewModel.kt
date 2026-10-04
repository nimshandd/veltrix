package com.example.gpsspeedometer

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpsspeedometer.data.AppDatabase
import com.example.gpsspeedometer.data.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

enum class GpsStatus {
    ACQUIRING,
    READY,
    WEAK,
    UNAVAILABLE
}

data class SpeedometerUiState(
    val hasPermission: Boolean = false,
    val gpsEnabled: Boolean = false,
    val gpsStatus: GpsStatus = GpsStatus.ACQUIRING,
    val speedKmh: Double = 0.0,
    val rawSpeedKmh: Double = 0.0,
    val accuracyMeters: Float? = null,
    val speedAccuracyMetersPerSecond: Float? = null,
    val maxSpeedKmh: Double = 0.0,
    val distanceKm: Double = 0.0,
    val tripDurationMs: Long = 0L,
    val averageSpeedKmh: Double = 0.0,
    val isTracking: Boolean = false,
    val isFiltered: Boolean = false,
    val message: String? = null,
    val lastFixAt: Long = 0L
)

class SpeedometerViewModel(application: Application) : AndroidViewModel(application) {
    private val tripDao = AppDatabase.getInstance(application).tripDao()
    private val speedFilter = SpeedFilter()
    private var lastLocation: DistanceSample? = null
    private var tripStartedAt: Long = 0L
    private var tripDistanceMeters: Double = 0.0
    private var maxSpeedKmh: Double = 0.0

    private val _uiState = MutableStateFlow(SpeedometerUiState())
    val uiState: StateFlow<SpeedometerUiState> = _uiState

    val trips = tripDao.observeTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshPermissionState(context: Context) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        _uiState.value = _uiState.value.copy(hasPermission = hasPermission)
    }

    fun updatePermissionResult(results: Map<String, Boolean>) {
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        _uiState.value = _uiState.value.copy(hasPermission = granted)
        if (!granted) {
            _uiState.value = _uiState.value.copy(message = "Location permission is required to measure GPS speed.")
        }
    }

    fun onProviderStateChanged(gpsAvailable: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(
            gpsEnabled = gpsAvailable,
            gpsStatus = if (gpsAvailable) current.gpsStatus else GpsStatus.UNAVAILABLE,
            message = if (!gpsAvailable) "GPS is disabled. Enable location services to continue." else current.message
        )
    }

    fun onLocationChanged(location: Location) {
        val gpsAccuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else null
        val speedAccuracy = if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond.toDouble() else null
        val rawSpeedKmh = if (location.hasSpeed()) location.speed.toDouble() * 3.6 else 0.0
        val smoothedSpeed = speedFilter.update(rawSpeedKmh, gpsAccuracy, speedAccuracy)

        val currentStatus = when {
            gpsAccuracy != null && gpsAccuracy > 50.0 -> GpsStatus.WEAK
            smoothedSpeed <= 0.5 && rawSpeedKmh <= 0.5 -> GpsStatus.ACQUIRING
            else -> GpsStatus.READY
        }

        val current = _uiState.value
        val timestamp = System.currentTimeMillis()
        val previous = lastLocation
        if (_uiState.value.isTracking && previous != null) {
            val elapsedMs = (location.time - previous.timeMs).coerceAtLeast(0L)
            val deltaMeters = DistanceValidator.deltaMeters(previous, DistanceSample(
                latitude = location.latitude,
                longitude = location.longitude,
                timeMs = location.time,
                accuracyMeters = gpsAccuracy?.toFloat() ?: 25f,
                speedAccuracyMetersPerSecond = speedAccuracy?.toFloat()
            ))
            val valid = DistanceValidator.isValidDelta(
                previous = previous,
                current = DistanceSample(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timeMs = location.time,
                    accuracyMeters = gpsAccuracy?.toFloat() ?: 25f,
                    speedAccuracyMetersPerSecond = speedAccuracy?.toFloat()
                ),
                elapsedMs = elapsedMs,
                gpsAccuracyMeters = gpsAccuracy?.toFloat() ?: 25f,
                speedAccuracyMps = speedAccuracy?.toFloat()
            )
            if (valid && deltaMeters >= 0.0) {
                tripDistanceMeters += deltaMeters
                maxSpeedKmh = max(maxSpeedKmh, smoothedSpeed)
            }
        }

        lastLocation = DistanceSample(
            latitude = location.latitude,
            longitude = location.longitude,
            timeMs = location.time,
            accuracyMeters = gpsAccuracy?.toFloat() ?: 25f,
            speedAccuracyMetersPerSecond = speedAccuracy?.toFloat()
        )

        val durationMs = if (_uiState.value.isTracking) {
            System.currentTimeMillis() - tripStartedAt
        } else {
            0L
        }

        val averageSpeedKmh = if (durationMs > 0L && tripDistanceMeters > 0.0) {
            val hours = durationMs / 3_600_000.0
            if (hours > 0.0) (tripDistanceMeters / 1000.0) / hours else 0.0
        } else {
            0.0
        }

        _uiState.value = current.copy(
            gpsEnabled = true,
            gpsStatus = currentStatus,
            speedKmh = smoothedSpeed,
            rawSpeedKmh = rawSpeedKmh,
            accuracyMeters = gpsAccuracy?.toFloat(),
            speedAccuracyMetersPerSecond = speedAccuracy?.toFloat(),
            maxSpeedKmh = if (_uiState.value.isTracking) maxSpeedKmh else current.maxSpeedKmh,
            distanceKm = if (_uiState.value.isTracking) tripDistanceMeters / 1000.0 else current.distanceKm,
            tripDurationMs = durationMs,
            averageSpeedKmh = averageSpeedKmh,
            isFiltered = abs(smoothedSpeed - rawSpeedKmh) > 0.5 && rawSpeedKmh > 0.0,
            lastFixAt = timestamp,
            message = if (currentStatus == GpsStatus.WEAK) "Weak GPS signal. Speed is less reliable." else current.message
        )
    }

    fun startTrip() {
        tripStartedAt = System.currentTimeMillis()
        tripDistanceMeters = 0.0
        maxSpeedKmh = 0.0
        lastLocation = null
        speedFilter.reset()
        _uiState.value = _uiState.value.copy(
            isTracking = true,
            gpsStatus = _uiState.value.gpsStatus,
            maxSpeedKmh = 0.0,
            distanceKm = 0.0,
            tripDurationMs = 0L,
            averageSpeedKmh = 0.0,
            message = "Trip started."
        )
    }

    fun stopTrip() {
        val started = tripStartedAt
        val ended = System.currentTimeMillis()
        val durationMs = (ended - started).coerceAtLeast(0L)
        val average = if (durationMs > 0L && tripDistanceMeters > 0.0) {
            val hours = durationMs / 3_600_000.0
            if (hours > 0.0) (tripDistanceMeters / 1000.0) / hours else 0.0
        } else {
            0.0
        }

        val finalTrip = Trip(
            startedAt = started,
            endedAt = ended,
            distanceMeters = tripDistanceMeters,
            maxSpeedKmh = maxSpeedKmh,
            averageSpeedKmh = average
        )

        viewModelScope.launch {
            tripDao.insert(finalTrip)
        }

        tripStartedAt = 0L
        tripDistanceMeters = 0.0
        maxSpeedKmh = 0.0
        lastLocation = null
        speedFilter.reset()

        _uiState.value = _uiState.value.copy(
            isTracking = false,
            maxSpeedKmh = 0.0,
            distanceKm = 0.0,
            tripDurationMs = durationMs,
            averageSpeedKmh = average,
            message = "Trip saved to history."
        )
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
