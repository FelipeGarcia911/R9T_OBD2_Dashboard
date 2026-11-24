# R9T OBD2 Dashboard

A native Android diagnostics project for my 2023 BMW R nineT Scrambler. The goal is a landscape dashboard with a tachometer, a central digital speedometer in km/h, a gear indicator, local time, and total distance in kilometers. The first hardware milestone is to read engine RPM and vehicle speed through a Bluetooth ELM327 Mini.

## What you can try today

The debug build includes an interactive Compose diagnostic screen with simulated telemetry. Select **Connect** to watch the Bluetooth, ELM327, and ECU stages progress. The simulation begins with zero vehicle speed, then varies both readings and briefly drops the speed signal. Select **Disconnect** and reconnect to start a fresh session. **Simulate ECU timeout** demonstrates how a failure is reported.

The diagnostics screen also shows simulated gear and odometer readings alongside the phone's local time. The OBD-II session log explains each command, shows raw replies and decoded values, and can be copied for troubleshooting. The simulator keeps the current session in memory, preserving connection events and up to 120 entries in total. Copy the log before starting another session.

The demo produces two RPM samples and one speed sample per simulated second when both readings are available. These rates are fixtures for testing the interface. Torque has already displayed RPM and speed with the reference motorcycle and adapter. Live Bluetooth integration and a side-by-side comparison with Torque are pending.

## Engineering approach

I separated the session contract from Bluetooth and the UI so each part can evolve and be verified independently. The debug implementation supplies deterministic session states through the same Kotlin interface the hardware implementation will use. The ViewModel converts those states to UI state and computes update rates from sample timestamps. Compose renders the result.

| Area | Responsibility |
| --- | --- |
| `diagnostics/application` | Session contract, connection states, typed errors, and timestamped readings |
| `diagnostics/data` | Pure Kotlin decoder for supported PIDs, RPM, and vehicle speed |
| `diagnostics/ui` | ViewModel, Compose screen, and previews |
| `src/debug` | Demo entry point and scripted telemetry |

The decoder tests cover valid readings, zero values, missing or malformed responses, and unrelated PIDs. The project uses Kotlin, Jetpack Compose, Material 3, AndroidX ViewModel, and coroutines. The [AI-DLC roadmap](ROADMAP.md) records work units, dependencies, and acceptance criteria.

## Run locally

1. Open the project in Android Studio and sync Gradle. Install Android SDK Platform 37 and use a device or emulator running Android 14 (API 34) or later.
2. Select the `debug` build variant and run the `app` configuration.
3. Use the diagnostics controls to explore the simulated session. The debug demo requires only the Android device or emulator.

Build the debug APK and run JVM tests from the repository root:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

## Next milestones

- Confirm the transport used by the reference ELM327 Mini.
- Integrate the read-only connection and validate readings and update rates on the motorcycle.
- Prototype all five dashboard indicators with simulated telemetry, then use the field results to tune RPM and speed. Confirm reliable sources for gear and total distance before displaying them as live values.

## Author

[Felipe Garcia](https://github.com/FelipeGarcia911) · Android development, diagnostic data modeling, and UI architecture.
