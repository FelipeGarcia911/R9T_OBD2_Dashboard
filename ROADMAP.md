# R9T OBD2 Dashboard Roadmap

The native Android app will display live data from the 2023 BMW R nineT Scrambler through a Bluetooth ELM327 Mini. The first release will provide a read-only diagnostic screen. Torque has already displayed engine RPM, vehicle speed, and other sensor readings with this setup. The Android app still needs to reproduce those readings and measure their update rates.

## 1. Inception · Scope and decisions

- **Project baseline.** The Kotlin and Jetpack Compose project is in a local Git repository on `main`.
- **MVP.** Connect to the adapter, distinguish the Bluetooth link from the ELM327 and ECU responses, and display the detected protocol, RPM, speed, and session errors.
- **Open decision.** RFCOMM/SPP is the first implementation. The reference adapter's transport remains unverified. If it requires BLE, identify its GATT service and characteristics and implement the same transport interface.
- **Field reference.** Record the phone model, Android version, Torque configuration, and observed sensors. Use RPM and speed as the primary comparison signals.
- **Planning status.** The MVP scope, unit dependencies, acceptance criteria, and Clean Architecture boundaries are agreed. Planning is complete.
- **Construction handoff.** Start UOW-00 (diagnostic contract), UOW-01 (reference setup), and UOW-03 (decoder). UOW-02 starts with RFCOMM as a working assumption. Confirm it on the reference hardware in UOW-01 and UOW-02. The two UI units can start once UOW-00 closes.

## 2. Construction · Units of work

### UOW-00 · Define the diagnostic contract

**Depends on:** The MVP scope.

- **Scope.** Define a small Kotlin application port for starting and stopping a session and observing typed session state. Model Bluetooth, adapter, and ECU progress separately. Represent RPM, speed in km/h, sample timestamps, unavailable values, and typed failures. Keep Android Bluetooth types, raw OBD-II frames, and localized messages outside the contract.
- **Done when.** UI and data work can use the same contract, with agreed examples for valid zero, missing data, stale readings, and reconnect. Keep the contract and implementations in the existing app module.
- **Contract examples.** Stationary speed is a timestamped sample of `0.0`. A supported signal without a current value is `Unavailable`. Disconnect and failure states carry no readings. Reconnect begins at `ConnectingBluetooth` and publishes fresh samples only after the session is live.

### UOW-01 · Confirm the reference setup

**Depends on:** Access to the motorcycle, adapter, phone, and Torque.

- **Scope.** Record Torque's connection setting, the adapter name, phone model, Android version, and observed sensors. Identify RFCOMM/SPP or the BLE GATT service and characteristics required for this adapter.
- **Done when.** The repository records the selected transport and Torque shows zero speed while stationary and live RPM with the engine running. Save the observations as the comparison baseline for UOW-05. A missing transport detail stays open until verified.

### UOW-02 · Connect to the adapter

**Depends on:** UOW-01 for hardware acceptance. Implementation can proceed with RFCOMM as the initial assumption.

- **Scope.** Implement the selected transport, adapter selection, Android permissions, and a minimal connect/disconnect control. Bound connection attempts and release resources on cancellation or link loss.
- **Done when.** On the physical phone, the user can select the adapter, connect, disconnect, and reconnect. Permission denial, disabled Bluetooth, and an unreachable adapter each produce actionable feedback. Verification ends at the Bluetooth link.

### UOW-03 · Decode supported PIDs, RPM, and speed

**Depends on:** Sample responses. Can start independently of UOW-01 and UOW-02.

- **Scope.** Implement a pure Kotlin decoder for `4100`, `410C`, and `410D`. Return the supported PID flags, engine RPM, and speed in km/h.
- **Done when.** Unit tests verify the support flags, exact values, valid zero, truncated or malformed frames, unrelated PIDs, and `NO DATA`. Missing or invalid values stay unavailable. The decoder has no Android dependencies.
- **Implementation decision.** The decoder accepts headerless ELM327 output, so UOW-04 must configure that format. For repeated responses, it uses the first valid matching frame.

### UOW-04 · Run the read-only diagnostic session

**Depends on:** UOW-00, UOW-02, and UOW-03.

- **Scope.** Initialize the ELM327, query `0100`, report the protocol using `ATDP` after a valid ECU response, and poll `010C` and `010D` for the supported signals. Expose session status and readings to the ViewModel.
- **Done when.** Tests with a fake transport verify command order, separate adapter and ECU states, and cover a timeout, an unsupported PID, and cancellation. On the motorcycle, the app receives a valid ECU response and reports its protocol. The session releases the transport when stopped.

### UOW-05A · Build the diagnostic UI with simulated telemetry

**Depends on:** UOW-00. Can run alongside UOW-01 through UOW-04.

- **Scope.** Build the ViewModel and Compose diagnostic screen with a deterministic fake implementation of the application port. Show session progress, protocol, RPM, speed, errors, and per-signal update rates. Use Compose previews for static states and the fake for interactive flows in debug builds and tests.
- **Done when.** Without Bluetooth hardware, the screen demonstrates connect, live updates, zero, missing data, timeout, disconnect, and reconnect. A controlled sample cadence verifies the displayed rate. The release build uses the real implementation when integrated.

### UOW-05 · Integrate and validate the diagnostic MVP

**Depends on:** UOW-01, UOW-04, and UOW-05A.

- **Scope.** Wire the real implementation at the app entry point and validate the same diagnostic screen on the reference hardware.
- **Done when.** Run the app and Torque separately on the same equipment under comparable conditions. Record the sample duration, readings, and per-signal update rates. Compare stationary speed, idle RPM, and nonzero speed in a controlled test with the phone operated by someone other than the rider. Verify unavailable versus zero, stale readings after disconnect, and fresh readings after reconnect. Record discrepancies and resolve those that affect the MVP before closing the unit.

### UOW-06A · Prototype the dashboard instruments

**Depends on:** UOW-00. Can run alongside UOW-05A and hardware work.

- **Scope.** Design an original landscape Compose dashboard with a sweeping tachometer, a prominent digital speedometer in km/h, a gear indicator, a local clock, and a total-distance readout in kilometers. Use simulated RPM, speed, gear, and distance. The clock uses the phone's local time.
- **Done when.** All five indicators are visible and readable together in landscape. Previews cover live, zero, and unavailable values. The simulated stream exercises different RPM and speed cadences, gear changes, and a missing reading.

### UOW-06 · Integrate the first dashboard

**Depends on:** UOW-05, UOW-06A, and review of the field results.

- **Scope.** Connect RPM and speed to the existing session state and tune their presentation to the observed update rates. Keep the diagnostic screen available for troubleshooting. Show gear and total distance when their sources have been verified in UOW-07 and UOW-08 respectively.
- **Done when.** The pilot phone shows live RPM, speed, and local time. Zero speed and unavailable readings are distinguishable, stale telemetry clears on disconnect, and fresh telemetry appears after reconnect.

### UOW-07 · Verify and integrate gear position

**Depends on:** UOW-05 and UOW-06A.

- **Scope.** Check whether the reference motorcycle exposes a trustworthy gear signal, including neutral. Display a gear only when its source is verified.
- **Done when.** A controlled comparison confirms the gear and neutral states, or the dashboard shows gear as unavailable and the investigation outcome is recorded. Any inferred gear feature requires its own validation plan.

### UOW-08 · Verify and integrate total distance

**Depends on:** UOW-05 and UOW-06A.

- **Scope.** Check whether the reference motorcycle exposes an odometer reading. Verify its units against the motorcycle and display the verified value in kilometers.
- **Done when.** The value agrees with the motorcycle's odometer, or total distance appears as unavailable and the investigation outcome is recorded.

### Delivery checkpoints

- **Current status.** UOW-00 and UOW-03 provide the session contract and tested decoder. UOW-05A has a debug demo with scripted telemetry and unit tests for the displayed rates. RFCOMM and the read-only ELM327 session are implemented. UOW-02, UOW-04, and UOW-05 still require validation on the reference phone, adapter, and motorcycle. UOW-01 and UOW-06A can proceed independently.
- **First functional release.** Close UOW-00 through UOW-05, including UOW-05A, with the diagnostic app verified on the target equipment.
- **Dashboard release.** Close UOW-06 after reviewing the diagnostic results. Track verified gear and odometer integration in UOW-07 and UOW-08.
- **Close each unit.** Record its build, applicable test results, and field evidence in the repository. Review any proposed scope changes before adding them to a unit.

Apply Clean Architecture's dependency rule by keeping the application port and models in Kotlin. The ViewModel depends on that port and maps its output to immutable UI state. Compose renders state and sends user actions to the ViewModel. The real OBD-II implementation and the debug fake both implement the same application port. The app entry point chooses the implementation. Within the real implementation, the ELM327 session uses a separate transport interface so RFCOMM or GATT details stay in the Bluetooth adapter. Test the protocol with a fake transport and the UI with a fake application port. End each *bolt* with a build, applicable tests, and a review of the evidence. [The Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html) · [Android UI layer](https://developer.android.com/topic/architecture/ui-layer)

Use English for code, technical comments, documentation, app text, and Git commit messages. Keep this conversation in Spanish.

## 3. Operations · Pilot and maintenance

Prepare a test APK for the physical Android device and record the installed version alongside the validated setup. Run a smoke test covering connection, readings, disconnection, and reconnection. Record decisions, reproducible limitations, and field results in the repository. Further distribution depends on the pilot results.

[AWS · AI-Driven Development Life Cycle](https://aws.amazon.com/blogs/devops/ai-driven-development-life-cycle/)
