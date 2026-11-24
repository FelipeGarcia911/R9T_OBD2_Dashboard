package com.felipeg.r9t_obd2_dashboard.diagnostics.ui

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
            val startedAt = System.nanoTime() / 1_000_000
            var tick = 0L
            var speed: SignalReading = SignalReading.Unavailable
            while (isActive) {
                val timestamp = startedAt + tick * 500
                val position = (tick % 12).toInt()
                val rpmValue = DEMO_RPM[position]
                val rpm = SignalReading.Sample(rpmValue.toDouble(), timestamp)
                val encodedRpm = rpmValue * 4
                record(
                    DiagnosticStage.POLLING, "Engine speed · $rpmValue rpm", "010C",
                    "41 0C ${hex(encodedRpm shr 8)} ${hex(encodedRpm and 0xFF)}",
                )
                if (tick % 2 == 0L) {
                    speed = if (position == 8) SignalReading.Unavailable
                    else SignalReading.Sample(position * 5.0, timestamp)
                    val speedValue = position * 5
                    if (speed is SignalReading.Unavailable) {
                        record(DiagnosticStage.POLLING, "Vehicle speed unavailable", "010D", "NO DATA")
                    } else {
                        record(DiagnosticStage.POLLING, "Vehicle speed · $speedValue km/h", "010D",
                            "41 0D ${hex(speedValue)}")
                    }
                }
                mutableState.value = DiagnosticState.Live(
                    protocol = "Simulated CAN",
                    engineRpm = rpm,
                    speedKmh = speed,
                    gear = if (position == 0) GearPosition.Neutral else GearPosition.Forward(1 + position / 3),
                    odometerKm = SignalReading.Sample(12_480.0 + tick * 0.01, timestamp),
                )
                delay(500)
                tick++
            }
        }
    }

    override suspend fun stop() {
        session?.cancelAndJoin()
        session = null
        mutableState.value = DiagnosticState.Disconnected
        record(DiagnosticStage.BLUETOOTH, "Session disconnected")
    }

    private companion object {
        val DEMO_RPM = intArrayOf(1_100, 1_500, 2_600, 3_800, 4_900, 6_300,
            7_600, 8_450, 6_900, 4_600, 2_900, 1_700)
    }

    private fun hex(value: Int): String = value.toString(16).uppercase().padStart(2, '0')

    private fun record(
        stage: DiagnosticStage,
        description: String,
        command: String? = null,
        response: String? = null,
    ) {
        val entry = DiagnosticTraceEntry(System.currentTimeMillis(), stage, description, command, response)
        val entries = mutableTrace.value + entry
        val oldestReading = entries.indexOfFirst { it.stage == DiagnosticStage.POLLING }
        mutableTrace.value = if (entries.size > 120 && oldestReading >= 0) {
            entries.toMutableList().apply { removeAt(oldestReading) }
        } else {
            entries.takeLast(120)
        }
    }
}
