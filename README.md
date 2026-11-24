# R9T OBD2 Dashboard

A native Android diagnostics project for my 2023 BMW R nineT Scrambler. The goal is a landscape dashboard with a tachometer, a central digital speedometer in km/h, a gear indicator, local time, and total distance in kilometers. The first hardware milestone is to read engine RPM and vehicle speed through a Bluetooth ELM327 Mini.

## What you can try today

The debug build includes an interactive Compose diagnostic screen with simulated telemetry. Select **Show simulation**, then **Connect** to watch the Bluetooth, ELM327, and ECU stages progress. The simulation begins with zero vehicle speed, then varies both readings and briefly drops the speed signal. Select **Disconnect** and reconnect to start a fresh session. **Simulate ECU timeout** demonstrates how a failure is reported.

The diagnostics screen also shows simulated gear and odometer readings alongside the phone's local time. The OBD-II session log explains each command, shows raw replies and decoded values, and can be copied for troubleshooting. The simulator keeps the current session in memory, preserving connection events and up to 120 entries in total. Copy the log before starting another session.

The demo produces two RPM samples and one speed sample per simulated second when both readings are available. These rates are fixtures for testing the interface. Torque has already displayed RPM and speed with the reference motorcycle and adapter. Direct RFCOMM was verified on the reference motorcycle. A stationary field capture showed 1,831.5 RPM and 0 km/h over ISO 15765-4 CAN (11-bit, 500 kbit/s).

## Engineering approach

I separated the session contract from Bluetooth and the UI so each part can evolve and be verified independently. The debug implementation supplies deterministic session states through the same Kotlin interface the hardware implementation will use. The ViewModel converts those states to UI state and computes update rates from sample timestamps. Compose renders the result.

| Area | Responsibility |
| --- | --- |
| `diagnostics/application` | Session contract, connection states, typed errors, and timestamped readings |
| `diagnostics/data` | ELM327 session, replaceable transport, RFCOMM implementation, and pure Kotlin PID decoder |
| `diagnostics/ui` | ViewModel, Compose screen, and previews |
| `src/debug` | Demo entry point and scripted telemetry |

The decoder tests cover valid readings, zero values, missing or malformed responses, and unrelated PIDs. The project uses Kotlin, Jetpack Compose, Material 3, AndroidX ViewModel, and coroutines. The [AI-DLC roadmap](ROADMAP.md) records work units, dependencies, and acceptance criteria. The [OBD-II telemetry inventory](docs/OBD_TELEMETRY.md) lists the PIDs verified on the motorcycle, example readings, units, and the state exposed to screens.

## Run locally

1. Open the project in Android Studio and sync Gradle. Install Android SDK Platform 37 and use a device or emulator running Android 14 (API 34) or later.
2. Select the `debug` build variant and run the `app` configuration.
3. Choose **Show simulation → Open dashboard** to try the five-instrument prototype. Rotate the device for the landscape layout. Select **Diagnostics** to return to the session without restarting it.

### Replay a diagnostic capture

In **Show simulation**, select **Example capture** or **Open capture** to choose an exported ELM327 JSON Lines file. Select **Play capture** to replay its decoded readings, timings, and errors in diagnostics. **Open dashboard** uses the same playback source. Replay runs locally and does not open a Bluetooth connection. Gear and distance are unavailable unless the data source provides them.

### Test a physical adapter

The app opens an RFCOMM connection directly to the ELM327. Torque is optional and serves as a separate comparison tool during field testing.

1. Power the ELM327 and pair it in Android Bluetooth settings. Close any OBD-II app already connected to the adapter.
2. In live diagnostics, select **Connect**, allow **Nearby devices**, and choose the paired adapter.
3. Look for the `ATI` adapter response and then a valid `0100` ECU response in the session log. Use **Session logs** to select and export the complete JSON Lines capture. In **Settings**, use **Detailed ELM327 logging** to control file capture for the next connection. It is on by default. Captures are saved privately on the phone until exported. Open **Dashboard** from live diagnostics to view the current RPM, speed, and the phone's local time. The motorcycle's standard PID support scan ended at `0180` without advertising gear (`01A4`) or odometer (`01A6`), so those fields remain unavailable in live mode.

Build the debug APK and run JVM tests from the repository root:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

## Next milestones

- Confirm the transport used by the reference ELM327 Mini.
- Validate the read-only RFCOMM connection, readings, and update rates on the motorcycle. Add a GATT transport if the reference adapter requires BLE.
- Use field results to tune RPM and speed in the dashboard prototype. The prototype marks 8,500 RPM as the start of the red zone, based on the maximum engine speed published in the [BMW Motorrad R nineT Scrambler rider's manual](https://manuals.bmw-motorrad.com/manuals/BA-Extern/IN/BA-INTERNET-COM/PDF/R_0L71_RM_0421_01.pdf). Confirm reliable sources for gear and total distance before displaying them as live values.

## Author

[Felipe Garcia](https://github.com/FelipeGarcia911) · Android development, diagnostic data modeling, and UI architecture.
