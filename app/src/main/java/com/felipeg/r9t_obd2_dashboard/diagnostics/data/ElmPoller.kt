package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.EngineVitalPids
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryReading
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetrySnapshot
import kotlinx.coroutines.delay

/**
 * Reads a connected ECU until the link fails: RPM, and speed with it when the ECU answers both at
 * once, every cycle, then whatever secondary request [PollingSchedule] allows. Every new state
 * goes to [publish].
 */
internal class ElmPoller(
    private val channel: ElmChannel,
    private val recorder: SessionRecorder,
    private val publish: (DiagnosticState.Live) -> Unit,
) {
    /** Whether this session's adapter accepted the single-reply count, decided by [probeCombined]. */
    private var singleReply = false

    /** Exits only by exception: an exchange failure or cancellation during a pause. */
    suspend fun poll(link: EcuLink): Nothing {
        recorder.stage = DiagnosticStage.POLLING
        val advertised = link.advertisedPids
        val sensors = SensorRotation(ObdTelemetryCatalog.pids.filter { it.id in advertised }, EngineVitalPids.all)
        val speedSupported = ObdPids.VEHICLE_SPEED in advertised
        val rpmSupported = ObdPids.ENGINE_RPM in advertised

        val probe = if (rpmSupported && speedSupported) probeCombined() else null
        val combined = probe != null
        var live = if (combined) {
            DiagnosticState.Live(link.protocol, probe.first, probe.second, telemetry = TelemetrySnapshot(advertised))
        } else {
            DiagnosticState.Live(
                link.protocol, readRpm(rpmSupported), readSpeed(speedSupported),
                telemetry = TelemetrySnapshot(advertised),
            )
        }
        publish(live)
        val schedule = PollingSchedule(
            pollSpeedSeparately = speedSupported && !combined,
            hasSensors = !sensors.isEmpty,
            startedAt = monotonicMillis(),
        )
        delay(PRIMARY_PERIOD_MS)

        while (true) {
            val cycleStartedAt = monotonicMillis()
            if (combined) {
                val (rpm, speed) = readRpmAndSpeed()
                live = live.copy(engineRpm = rpm, speedKmh = speed)
                publish(live)
            } else if (rpmSupported) {
                live = live.copy(engineRpm = readRpm(true))
                publish(live)
            }

            when (schedule.secondaryAt(monotonicMillis())) {
                SecondarySlot.SPEED -> {
                    live = live.copy(speedKmh = readSpeed(true))
                    schedule.speedRead(monotonicMillis())
                    publish(live)
                }
                SecondarySlot.SENSOR -> {
                    val pid = sensors.next()
                    val reading = readTelemetry(pid)
                    live = live.copy(telemetry = live.telemetry.copy(
                        readings = live.telemetry.readings + (pid.id to reading),
                    ))
                    schedule.sensorRead(monotonicMillis())
                    publish(live)
                }
                SecondarySlot.NONE -> Unit
            }

            delay(PollingSchedule.delayAfterCycle(cycleStartedAt, monotonicMillis()))
        }
    }

    /**
     * Probes once whether the ECU answers RPM and speed in one exchange, first telling the adapter
     * to expect a single reply. The first form that returns both readings is kept for the session,
     * and the single-reply count then applies to every request. Null when neither form works.
     */
    private suspend fun probeCombined(): Pair<SignalReading, SignalReading>? {
        for (singleReply in listOf(true, false)) {
            this.singleReply = singleReply
            val reading = readRpmAndSpeed()
            if (reading.first is SignalReading.Sample && reading.second is SignalReading.Sample) {
                recorder.record(
                    "Combined ${request(ObdPids.RPM_AND_SPEED)} supported · polling RPM and speed together" +
                        if (singleReply) " · single-reply requests" else "",
                )
                return reading
            }
        }
        singleReply = false
        recorder.record("Combined ${ObdPids.RPM_AND_SPEED} not supported · polling RPM and speed separately")
        return null
    }

    /** [base] as sent this session: with the single-reply count once the adapter accepted it. */
    private fun request(base: String): String = if (singleReply) ObdPids.singleResponse(base) else base

    private suspend fun readRpm(supported: Boolean): SignalReading =
        readSignal(supported, request(ObdPids.request(ObdPids.ENGINE_RPM)), "Engine speed", "rpm", ObdPidDecoder::engineRpm)

    private suspend fun readSpeed(supported: Boolean): SignalReading =
        readSignal(supported, request(ObdPids.request(ObdPids.VEHICLE_SPEED)), "Vehicle speed", "km/h") {
            ObdPidDecoder.speedKmh(it)?.toDouble()
        }

    private suspend fun readRpmAndSpeed(): Pair<SignalReading, SignalReading> {
        val command = request(ObdPids.RPM_AND_SPEED)
        val response = channel.transmit(command, "Engine and vehicle speed")
        val receivedAtMillis = monotonicMillis()
        val (rpm, speed) = ObdPidDecoder.rpmAndSpeed(response)
        recorder.record(
            "Engine speed · ${rpm ?: "unavailable"} rpm · Vehicle speed · ${speed ?: "unavailable"} km/h",
            command, response,
        )
        return sample(rpm, receivedAtMillis) to sample(speed, receivedAtMillis)
    }

    private suspend fun readTelemetry(pid: ObdTelemetryPid): TelemetryReading {
        val command = request(pid.command)
        val response = channel.transmit(command, pid.label)
        val reading = ObdTelemetryCatalog.read(pid, response, monotonicMillis())
        val summary = reading.measurements.joinToString(", ") { "${it.label}: ${it.value} ${it.unit}" }
        val detail = summary.ifEmpty { reading.status.name.lowercase().replace('_', ' ') }
        recorder.record("${pid.label} · $detail", command, response)
        return reading
    }

    private suspend fun readSignal(
        supported: Boolean,
        command: String,
        label: String,
        unit: String,
        decode: (String) -> Double?,
    ): SignalReading {
        if (!supported) return SignalReading.Unavailable
        val response = channel.transmit(command, label)
        val receivedAtMillis = monotonicMillis()
        val value = decode(response)
        recorder.record(if (value == null) "$label unavailable" else "$label · $value $unit", command, response)
        return sample(value, receivedAtMillis)
    }

    private fun sample(value: Double?, receivedAtMillis: Long): SignalReading =
        if (value == null) SignalReading.Unavailable else SignalReading.Sample(value, receivedAtMillis)

    private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000
}
