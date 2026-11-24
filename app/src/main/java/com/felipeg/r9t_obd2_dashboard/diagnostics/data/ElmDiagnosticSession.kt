package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticSession
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticTraceEntry
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryReading
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetrySnapshot
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val RPM_PERIOD_MS = 120L
private const val SPEED_PERIOD_MS = 250L
private const val OTHER_SENSOR_PERIOD_MS = 600L

internal class ElmDiagnosticSession(
    private val scope: CoroutineScope,
    private val transport: ElmTransport,
    private val startLog: suspend () -> ElmSessionLog? = { null },
) : DiagnosticSession {
    private val mutableState = MutableStateFlow<DiagnosticState>(DiagnosticState.Disconnected)
    private val mutableTrace = MutableStateFlow<List<DiagnosticTraceEntry>>(emptyList())
    override val state = mutableState.asStateFlow()
    override val trace = mutableTrace.asStateFlow()
    private var job: Job? = null
    private var stage = DiagnosticStage.BLUETOOTH
    private var pendingCommand: String? = null
    private var sessionLog: ElmSessionLog? = null

    override suspend fun start(adapterId: String) {
        if (job?.isActive == true) return
        mutableTrace.value = emptyList()
        stage = DiagnosticStage.BLUETOOTH
        pendingCommand = null
        mutableState.value = DiagnosticState.ConnectingBluetooth
        job = scope.launch {
            var endReason = "Session ended"
            try {
                sessionLog = startLog()
                record("Opening direct Bluetooth link to ELM327")
                transport.open(adapterId)
                record("Bluetooth link connected")
                stage = DiagnosticStage.ADAPTER
                mutableState.value = DiagnosticState.WaitingForAdapter
                exchange("ATZ", "Reset adapter")
                val identification = exchange("ATI", "Identify adapter")
                if (answer(identification) == null) invalidResponse()
                configure("ATE0", "Disable echo")
                configure("ATH0", "Disable headers")
                configure("ATSP0", "Select protocol automatically")

                stage = DiagnosticStage.ECU
                mutableState.value = DiagnosticState.WaitingForEcu
                val support = exchange("0100", "Read supported PIDs")
                val supported = ObdPidDecoder.supportedPids(support) ?: run {
                    val noData = support.substringBefore('>').lineSequence()
                        .any { it.trim().equals("NO DATA", ignoreCase = true) }
                    if (noData) throw ElmTransportException(DiagnosticError.NO_ECU_DATA)
                    invalidResponse()
                }
                recordPidSupport(supported, 0x0C, "010C", "Engine speed")
                recordPidSupport(supported, 0x0D, "010D", "Vehicle speed")
                val protocol = answer(exchange("ATDP", "Read active protocol"))
                val advertised = discoverSupportedPids(supported)
                stage = DiagnosticStage.POLLING
                poll(protocol, advertised)
            } catch (_: TimeoutCancellationException) {
                fail(DiagnosticError.TIMEOUT)
            } catch (_: ElmLogException) {
                loggingFailed()
            } catch (error: ElmTransportException) {
                fail(error.error)
            } catch (_: SecurityException) {
                fail(DiagnosticError.PERMISSION_DENIED)
            } catch (_: IOException) {
                fail(if (stage == DiagnosticStage.BLUETOOTH) DiagnosticError.ADAPTER_UNREACHABLE
                    else DiagnosticError.CONNECTION_LOST)
            } catch (cancelled: CancellationException) {
                endReason = "Session cancelled or disconnected"
                throw cancelled
            } finally {
                transport.close()
                withContext(NonCancellable) {
                    try {
                        val partial = transport.takePartialResponse()
                        if (partial != null) {
                            record("Incomplete response", pendingCommand, partial, ElmLogEvent.ERROR)
                        }
                        record(endReason, pendingCommand, event = ElmLogEvent.SESSION_END)
                    } catch (_: ElmLogException) {
                        loggingFailed()
                    } finally {
                        sessionLog = null
                    }
                }
            }
        }
    }

    override suspend fun stop() {
        job?.cancelAndJoin()
        job = null
        stage = DiagnosticStage.BLUETOOTH
        record("Session disconnected", pendingCommand)
        pendingCommand = null
        mutableState.value = DiagnosticState.Disconnected
    }

    private suspend fun configure(command: String, description: String) {
        if (exchange(command, description).substringBefore('>').lineSequence().none { it.trim() == "OK" }) {
            invalidResponse()
        }
    }

    private suspend fun exchange(command: String, description: String): String {
        val response = transmit(command, description)
        record(description, command, response)
        return response
    }

    private suspend fun transmit(command: String, description: String): String {
        pendingCommand = command
        sessionLog?.append(
            ElmLogEvent.TX_ATTEMPT,
            DiagnosticTraceEntry(System.currentTimeMillis(), stage, description, command),
        )
        return transport.exchange(command).also { pendingCommand = null }
    }

    private suspend fun discoverSupportedPids(initial: Set<Int>): Set<Int> {
        val supported = initial.toMutableSet()
        for (page in 0x20..0xA0 step 0x20) {
            if (page !in supported) break
            val command = "01" + page.toString(16).uppercase()
            val response = exchange(command, "Read extended PID support")
            val pagePids = ObdPidDecoder.supportedPids(response, page)
            if (pagePids == null) {
                record("Extended PID discovery stopped: no valid response for $command")
                return supported.toSet()
            }
            supported += pagePids
        }
        recordPidSupport(supported, 0xA4, "01A4", "Actual gear")
        recordPidSupport(supported, 0xA6, "01A6", "Odometer")
        if (0xA4 in supported) exchange("01A4", "Capture actual gear for field validation")
        if (0xA6 in supported) exchange("01A6", "Capture odometer for field validation")
        return supported.toSet()
    }

    private suspend fun poll(protocol: String?, advertised: Set<Int>) {
        val sensors = ObdTelemetryCatalog.pids.filter { it.id in advertised }
        val speedSupported = 0x0D in advertised
        val rpmSupported = 0x0C in advertised
        var nextSensor = 0
        var live = DiagnosticState.Live(
            protocol, readRpm(rpmSupported), readSpeed(speedSupported),
            telemetry = TelemetrySnapshot(advertised),
        )
        mutableState.value = live
        var nextSpeedAt = if (speedSupported) monotonicMillis() + SPEED_PERIOD_MS else Long.MAX_VALUE
        var nextSensorAt = if (sensors.isNotEmpty()) monotonicMillis() else Long.MAX_VALUE
        delay(RPM_PERIOD_MS)

        while (currentCoroutineContext().isActive) {
            val cycleStartedAt = monotonicMillis()
            if (rpmSupported) {
                live = live.copy(engineRpm = readRpm(true))
                mutableState.value = live
            }

            // Send at most one secondary request between RPM updates.
            val now = monotonicMillis()
            if (nextSpeedAt <= now && nextSpeedAt <= nextSensorAt) {
                live = live.copy(speedKmh = readSpeed(true))
                nextSpeedAt = monotonicMillis() + SPEED_PERIOD_MS
                mutableState.value = live
            } else if (nextSensorAt <= now) {
                val pid = sensors[nextSensor]
                val reading = readTelemetry(pid)
                live = live.copy(telemetry = live.telemetry.copy(
                    readings = live.telemetry.readings + (pid.id to reading),
                ))
                nextSensor = (nextSensor + 1) % sensors.size
                nextSensorAt = monotonicMillis() + OTHER_SENSOR_PERIOD_MS
                mutableState.value = live
            }

            delay((RPM_PERIOD_MS - (monotonicMillis() - cycleStartedAt)).coerceAtLeast(0))
        }
    }

    private suspend fun readRpm(supported: Boolean): SignalReading =
        readSignal(supported, "010C", "Engine speed", "rpm", ObdPidDecoder::engineRpm)

    private suspend fun readSpeed(supported: Boolean): SignalReading =
        readSignal(supported, "010D", "Vehicle speed", "km/h") { ObdPidDecoder.speedKmh(it)?.toDouble() }

    private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000

    private suspend fun readTelemetry(pid: ObdTelemetryPid): TelemetryReading {
        val response = transmit(pid.command, pid.label)
        val reading = ObdTelemetryCatalog.read(pid, response, System.nanoTime() / 1_000_000)
        val summary = reading.measurements.joinToString(", ") { "${it.label}: ${it.value} ${it.unit}" }
        val detail = summary.ifEmpty { reading.status.name.lowercase().replace('_', ' ') }
        record("${pid.label} · $detail", pid.command, response)
        return reading
    }

    private suspend fun recordPidSupport(supported: Set<Int>, pid: Int, request: String, label: String) {
        val availability = if (pid in supported) "supported" else "not advertised by the ECU"
        record("$label ($request) · $availability")
    }

    private suspend fun readSignal(
        supported: Boolean,
        command: String,
        label: String,
        unit: String,
        decode: (String) -> Double?,
    ): SignalReading {
        if (!supported) return SignalReading.Unavailable
        val response = transmit(command, label)
        val receivedAtMillis = System.nanoTime() / 1_000_000
        val value = decode(response)
        record(if (value == null) "$label unavailable" else "$label · $value $unit", command, response)
        return if (value == null) SignalReading.Unavailable
        else SignalReading.Sample(value, receivedAtMillis)
    }

    private fun answer(response: String): String? =
        response.substringBefore('>').lineSequence().map(String::trim)
            .firstOrNull { it.isNotEmpty() && it !in setOf("ATI", "ATDP", "?", "ERROR", "NO DATA") }

    private fun invalidResponse(): Nothing = throw ElmTransportException(DiagnosticError.INVALID_RESPONSE)

    private suspend fun fail(error: DiagnosticError) {
        try {
            val partial = transport.takePartialResponse()
            record(error.name.replace('_', ' ').lowercase(), pendingCommand, partial, ElmLogEvent.ERROR)
            mutableState.value = DiagnosticState.Failed(stage, error)
        } catch (_: ElmLogException) {
            loggingFailed()
        } finally {
            pendingCommand = null
        }
    }

    private fun loggingFailed() {
        sessionLog = null
        mutableState.value = DiagnosticState.Failed(stage, DiagnosticError.LOG_WRITE_FAILED)
    }

    private suspend fun record(
        description: String,
        command: String? = null,
        response: String? = null,
        event: ElmLogEvent = if (response == null) ElmLogEvent.EVENT else ElmLogEvent.RX,
    ) {
        val entry = DiagnosticTraceEntry(System.currentTimeMillis(), stage, description, command, response)
        sessionLog?.let { log ->
            withContext(NonCancellable) { log.append(event, entry) }
        }
        if (event == ElmLogEvent.SESSION_END) return
        val entries = mutableTrace.value + entry
        val oldestReading = entries.indexOfFirst {
            it.stage == DiagnosticStage.POLLING && it.response != null
        }
        mutableTrace.value = if (entries.size > 120 && oldestReading >= 0) {
            entries.toMutableList().apply { removeAt(oldestReading) }
        } else {
            entries.takeLast(120)
        }
    }
}
