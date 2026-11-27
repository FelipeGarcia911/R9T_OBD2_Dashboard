package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.EngineVitalPids
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryMeasurement
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryReading
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetrySnapshot
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryStatus
import kotlin.math.sin
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticSession
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticTraceEntry
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.GearPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal class SimulatedDiagnosticSession(private val scope: CoroutineScope) : DiagnosticSession {
    private val mutableState = MutableStateFlow<DiagnosticState>(DiagnosticState.Disconnected)
    override val state = mutableState.asStateFlow()
    private val mutableTrace = MutableStateFlow<List<DiagnosticTraceEntry>>(emptyList())
    override val trace = mutableTrace.asStateFlow()
    private var session: Job? = null

    override suspend fun start(adapterId: String) {
        if (session?.isActive == true) return
        mutableTrace.value = emptyList()
        record(DiagnosticStage.BLUETOOTH, "Opening simulated link")
        mutableState.value = DiagnosticState.ConnectingBluetooth
        session = scope.launch {
            delay(400.milliseconds)
            record(DiagnosticStage.BLUETOOTH, "Link connected")
            mutableState.value = DiagnosticState.WaitingForAdapter
            delay(400.milliseconds)
            record(DiagnosticStage.ADAPTER, "Identify adapter", "ATI", "ELM327 (simulated)")
            mutableState.value = DiagnosticState.WaitingForEcu
            delay(600.milliseconds)
            if (adapterId == "demo-timeout") {
                record(DiagnosticStage.ECU, "Supported PIDs · Response timed out", "0100")
                mutableState.value = DiagnosticState.Failed(DiagnosticStage.ECU, DiagnosticError.TIMEOUT)
                return@launch
            }

            record(DiagnosticStage.ECU, "Supported PIDs · RPM and speed", "0100", "41 00 00 18 00 00")
            record(DiagnosticStage.ADAPTER, "Detected protocol", "ATDP", "Simulated CAN")
            pollLikeTheBike()
        }
    }

    /**
     * Asks every [POLL_MILLIS] like the live session, and gets [EcuSampler]'s answers about
     * [SyntheticRide]: a new value only every ~200 ms, the same one repeated in between.
     */
    private suspend fun CoroutineScope.pollLikeTheBike() {
        val ecu = EcuSampler()
        val startedAt = System.nanoTime() / 1_000_000
        var odometer = 12_480.0
        var previousAt = startedAt
        while (isActive) {
            val now = System.nanoTime() / 1_000_000
            val elapsed = now - startedAt
            val reply = ecu.reply(now) { SyntheticRide.at(elapsed) }
            odometer += reply.speedKmh * (now - previousAt) / 3_600_000.0
            previousAt = now
            val raw = (reply.rpm * 4).toInt()
            val speed = reply.speedKmh.toInt()
            record(
                DiagnosticStage.POLLING,
                "Engine speed · ${reply.rpm} rpm · Vehicle speed · ${reply.speedKmh} km/h",
                ObdPids.singleResponse(ObdPids.RPM_AND_SPEED),
                "41 0C ${ObdPids.hex(raw shr 8)} ${ObdPids.hex(raw and 0xFF)} 0D ${ObdPids.hex(speed)} \r\r>",
            )
            mutableState.value = DiagnosticState.Live(
                protocol = "Simulated CAN",
                engineRpm = SignalReading.Sample(reply.rpm, now),
                speedKmh = SignalReading.Sample(reply.speedKmh, now),
                gear = reply.gear,
                odometerKm = SignalReading.Sample(odometer, now),
                telemetry = simulatedVitals(elapsed / 500, now),
            )
            delay(POLL_MILLIS)
        }
    }

    override suspend fun stop() {
        session?.cancelAndJoin()
        session = null
        mutableState.value = DiagnosticState.Disconnected
        record(DiagnosticStage.BLUETOOTH, "Session disconnected")
    }

    private companion object {
        /** The live session's pace with single-reply requests: ~15 requests a second. */
        const val POLL_MILLIS = 66L
    }

    /** Engine vitals within the ranges the reference bike reported, drifting slowly. */
    private fun simulatedVitals(tick: Long, timestamp: Long): TelemetrySnapshot {
        val drift = sin(tick / 20.0)
        fun reading(label: String, value: Double, unit: String) = TelemetryReading(
            label = label, receivedAtMillis = timestamp, payload = null,
            measurements = listOf(TelemetryMeasurement(label, value, unit)), status = TelemetryStatus.NUMERIC,
        )
        return TelemetrySnapshot(
            advertisedPids = EngineVitalPids.all,
            readings = mapOf(
                EngineVitalPids.ENGINE_TEMPERATURE to reading("Engine temperature", 116.0 + 6 * drift, "°C"),
                EngineVitalPids.OIL_TEMPERATURE to reading("Engine oil temperature", 100.0 + 4 * drift, "°C"),
                EngineVitalPids.MODULE_VOLTAGE to reading("Control module voltage", 14.1 - .2 * drift, "V"),
                EngineVitalPids.INTAKE_AIR_TEMPERATURE to reading("Intake air temperature", 33.0, "°C"),
            ),
        )
    }

    private fun record(
        stage: DiagnosticStage,
        description: String,
        command: String? = null,
        response: String? = null,
    ) {
        val entry = DiagnosticTraceEntry(System.currentTimeMillis(), stage, description, command, response)
        mutableTrace.value = mutableTrace.value.appendCapped(entry)
    }
}
