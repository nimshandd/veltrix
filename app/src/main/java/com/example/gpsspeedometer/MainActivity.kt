package com.example.gpsspeedometer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gpsspeedometer.data.AppDatabase
import com.example.gpsspeedometer.data.Trip
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

private val DarkBackground = Color(0xFF0B1015)
private val SurfaceDark = Color(0xFF101922)
private val CardDark = Color(0xFF111C2A)
private val LineDark = Color(0xFF233247)
private val TextPrimary = Color(0xFFEAF3FF)
private val TextSecondary = Color(0xFFB5C3D8)
private val Accent = Color(0xFF7AD9C1)
private val AccentSoft = Color(0xFF2FC8A5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GpsSpeedometerTheme {
                SpeedometerApp()
            }
        }
    }
}

@Composable
private fun GpsSpeedometerTheme(content: @Composable () -> Unit) {
    val darkColors = darkColorScheme(
        primary = Accent,
        background = DarkBackground,
        surface = SurfaceDark,
        onPrimary = Color.Black,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        secondary = AccentSoft,
        tertiary = Color(0xFF9FC1FF)
    )
    val lightColors = lightColorScheme(
        primary = AccentSoft,
        background = Color(0xFFF5F7FA),
        surface = Color.White,
        onBackground = Color(0xFF101822),
        onSurface = Color(0xFF101822),
        secondary = AccentSoft,
        tertiary = Color(0xFF7B9CFF)
    )
    MaterialTheme(colorScheme = darkColors, content = content)
}

@Composable
private fun SpeedometerApp(viewModel: SpeedometerViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val trips by viewModel.trips.collectAsStateWithLifecycle(initialValue = emptyList())
    val dao = remember { AppDatabase.getInstance(context).tripDao() }
    val scope = rememberCoroutineScope()
    val locationManager = remember(context) {
        context.getSystemService(LOCATION_SERVICE) as LocationManager
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.updatePermissionResult(results)
    }
    var showHistory by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        viewModel.refreshPermissionState(context)
    }

    DisposableEffect(uiState.hasPermission, locationManager) {
        if (!uiState.hasPermission) return@DisposableEffect onDispose {}

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (provider == null) {
            viewModel.onProviderStateChanged(false)
            return@DisposableEffect onDispose {}
        }

        viewModel.onProviderStateChanged(true)

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                viewModel.onLocationChanged(location)
            }

            override fun onProviderDisabled(provider: String) {
                viewModel.onProviderStateChanged(false)
            }

            override fun onProviderEnabled(provider: String) {
                viewModel.onProviderStateChanged(true)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                locationManager.requestLocationUpdates(provider, 1000L, 0f, listener)
            }
        } catch (_: SecurityException) {
            viewModel.onProviderStateChanged(false)
        }

        onDispose {
            try {
                locationManager.removeUpdates(listener)
            } catch (_: SecurityException) {
            }
        }
    }

    BackHandler(enabled = showHistory) { showHistory = false }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopBar(
                title = if (showHistory) "Trip history" else "Veltrix",
                onBack = { showHistory = false },
                onHistoryToggle = { showHistory = !showHistory }
            )
        }
    ) { padding ->
        if (showHistory) {
            HistoryScreen(
                trips = trips,
                onDelete = { id ->
                    scope.launch { dao.deleteById(id) }
                },
                modifier = Modifier.padding(padding)
            )
        } else {
            SpeedometerScreen(
                uiState = uiState,
                modifier = Modifier.padding(padding),
                onEnableLocation = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                onToggleTrip = {
                    if (uiState.isTracking) {
                        viewModel.stopTrip()
                    } else {
                        viewModel.startTrip()
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
    onHistoryToggle: () -> Unit
) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary) },
        navigationIcon = {
            if (title == "Trip history") {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
            }
        },
        actions = {
            IconButton(onClick = onHistoryToggle) {
                Icon(Icons.Default.History, contentDescription = "Trip history", tint = TextPrimary)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
    )
}

@Composable
private fun SpeedometerScreen(
    uiState: SpeedometerUiState,
    modifier: Modifier = Modifier,
    onEnableLocation: () -> Unit,
    onToggleTrip: () -> Unit
) {
    val speedDisplay = if (uiState.gpsStatus == GpsStatus.UNAVAILABLE || uiState.gpsStatus == GpsStatus.ACQUIRING) "--" else String.format("%.0f", uiState.speedKmh)
    val gaugeProgress = ((uiState.speedKmh.coerceIn(0.0, 200.0) / 200.0) * 270f).toFloat()
    val animatedProgress by animateFloatAsState(targetValue = gaugeProgress, label = "speedProgress")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        StatusRow(uiState)
        Spacer(Modifier.height(18.dp))

        Surface(
            color = CardDark,
            shape = RoundedCornerShape(32.dp),
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(260.dp)) {
                    drawArc(
                        color = LineDark,
                        startAngle = -225f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = 12f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Accent,
                        startAngle = -225f,
                        sweepAngle = animatedProgress,
                        useCenter = false,
                        style = Stroke(width = 12f, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CURRENT SPEED", color = TextSecondary, fontSize = 11.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            speedDisplay,
                            color = TextPrimary,
                            fontSize = 70.sp,
                            fontWeight = FontWeight.Light
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("km/h", color = TextSecondary, fontSize = 18.sp, modifier = Modifier.padding(bottom = 12.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (uiState.gpsStatus) {
                            GpsStatus.ACQUIRING -> "Waiting for a valid fix"
                            GpsStatus.WEAK -> "Weak GPS signal"
                            GpsStatus.UNAVAILABLE -> "GPS unavailable"
                            GpsStatus.READY -> if (uiState.isFiltered) "Filtered live speed" else "Live GPS speed"
                        },
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("MAX", if (uiState.maxSpeedKmh > 0) String.format("%.1f", uiState.maxSpeedKmh) else "--", "km/h", Modifier.weight(1f))
            MetricCard("DIST", if (uiState.distanceKm > 0 || uiState.isTracking) String.format("%.2f", uiState.distanceKm) else "0.00", "km", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("TIME", formatDuration(uiState.tripDurationMs), "", Modifier.weight(1f))
            MetricCard("ACCURACY", formatAccuracy(uiState.accuracyMeters), "m", Modifier.weight(1f))
        }
        Spacer(Modifier.height(22.dp))

        if (!uiState.hasPermission) {
            Button(
                onClick = onEnableLocation,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentSoft)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Enable location")
            }
        } else {
            Button(
                onClick = onToggleTrip,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isTracking) Color(0xFF2B3645) else AccentSoft
                )
            ) {
                Icon(Icons.Default.Speed, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (uiState.isTracking) "Stop trip" else "Start trip")
            }
        }

        uiState.message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = TextSecondary, fontSize = 12.sp)
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun StatusRow(uiState: SpeedometerUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        when (uiState.gpsStatus) {
                            GpsStatus.READY -> Color(0xFF45D391)
                            GpsStatus.WEAK -> Color(0xFFF3B94F)
                            GpsStatus.UNAVAILABLE -> Color(0xFFEA6F5B)
                            GpsStatus.ACQUIRING -> Color(0xFF7E8AA4)
                        },
                        shape = CircleShape
                    )
            )
            Spacer(Modifier.width(10.dp))
            Text(
                when (uiState.gpsStatus) {
                    GpsStatus.READY -> "GPS ready"
                    GpsStatus.WEAK -> "Weak signal"
                    GpsStatus.UNAVAILABLE -> "GPS unavailable"
                    GpsStatus.ACQUIRING -> "Acquiring fix"
                },
                color = TextSecondary,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
        }
        if (uiState.isFiltered) {
            Text("Filtered", color = Accent, fontSize = 10.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun HistoryScreen(
    trips: List<Trip>,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        if (trips.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No trip history yet", color = TextSecondary)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(trips, key = { it.id }) { trip ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDark)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(trip.startedAt)),
                                    fontSize = 14.sp,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onDelete(trip.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete trip", tint = TextSecondary)
                                }
                            }
                            Divider(color = LineDark, thickness = 1.dp)
                            Spacer(Modifier.height(10.dp))
                            Text("Distance: %.2f km".format(trip.distanceMeters / 1000.0), color = TextPrimary)
                            Text("Duration: ${formatDuration(trip.endedAt - trip.startedAt)}", color = TextSecondary)
                            Text("Average: %.1f km/h".format(trip.averageSpeedKmh), color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, fontSize = 10.sp, letterSpacing = 1.1.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 26.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                if (unit.isNotBlank()) {
                    Spacer(Modifier.width(5.dp))
                    Text(unit, color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

private fun formatAccuracy(accuracyMeters: Float?): String {
    return accuracyMeters?.takeIf { it > 0f }?.let { "±${it.toInt()}" } ?: "--"
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0L) "%02d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

