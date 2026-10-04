# GPS Speedometer

A polished Android GPS speedometer built with Kotlin, Jetpack Compose, Android location APIs, and Room local persistence. The app keeps the original project structure but upgrades the speed display, trip-distance filtering, and UI to a modern Material 3 dashboard.

## Features
- Smooth live speed display in km/h with adaptive EMA filtering
- GPS status states: acquiring, ready, weak signal, and unavailable
- Trip tracking with distance, elapsed time, and max speed
- Room-backed trip history with local deletion
- Material 3 dark-mode dashboard and responsive layout
- GPS permission and disabled-provider handling

## Requirements
- Android Studio Ladybug or newer
- Android SDK 35
- JDK 17
- An Android device or emulator with GPS enabled for real-world testing

## Project setup
1. Open the project in Android Studio.
2. Let Gradle sync complete.
3. If prompted, install Android SDK 35 and the required platform tools.
4. Connect a device or start an emulator.
5. Grant location permission when the app requests it.

## Build commands
From the project root:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

On Windows PowerShell, use:

```powershell
$env:JAVA_HOME = 'C:\Users\nimsh\Downloads\jdk17\jdk-17.0.12+7'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GPS smoothing and distance filtering
The app does not invent or fabricate measurements. It uses Android location callbacks at a normal 1-second interval and smooths the displayed speed using an adaptive exponential moving average (EMA).

Key behavior:
- raw speed is taken from `Location.speed` when available
- speed smoothing reacts to GPS accuracy and speed-accuracy values when the platform provides them
- low-speed motion is allowed to settle near zero without bouncing
- spikes and implausible jumps are rejected by a distance validator
- duplicate timestamps, stale timestamps, and impossible position changes are ignored
- the app preserves legitimate slow movement while filtering out impossible jumps

This means the displayed value is smoother and less jittery without claiming that smoothing improves the actual GPS measurement quality.

## Trip history and data model
Trip records are stored locally using Room in the existing `trips` table. Each trip stores:
- start time
- end time
- distance in metres
- max speed in km/h
- average speed in km/h

The history screen shows date, distance, duration, and average speed. Deletion uses the existing Room DAO and keeps the local data model compatible with the original project.

## UI summary
The redesigned app keeps a minimal speedometer-focused experience:
- large primary digital speed readout
- circular gauge progress indicator
- compact metric cards for max speed, trip distance, time, and accuracy
- GPS status and signal health indicators
- Start/Stop trip action with clear state feedback
- dedicated trip history screen with empty state

## Known limitations
- Background tracking is not enabled; the app tracks while the app is active and the location callback is registered.
- GPS can still be weak or unavailable indoors or in obstructed conditions.
- Real GPS accuracy depends on the device, environment, and signal quality.
- Local trip history remains on the device and is removed when the app is uninstalled.

## Verification
Verified in this environment with:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Both commands succeeded using Java 17.
