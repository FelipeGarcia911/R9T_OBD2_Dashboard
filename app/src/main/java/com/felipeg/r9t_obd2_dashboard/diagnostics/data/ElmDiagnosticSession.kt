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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Minimum time between primary requests. Field captures show ~75 ms per exchange. */
private const val PRIMARY_PERIOD_MS = 50L

/** Only used when the ECU rejects the combined request and speed must be polled separately. */
private const val SPEED_PERIOD_MS = 250L

/** One secondary sensor per second keeps the bus free for the dashboard readings. */
private const val OTHER_SENSOR_PERIOD_MS = 1_000L

/** Requests engine RPM and vehicle speed in one CAN exchange. */
private const val RPM_AND_SPEED = "010C0D"
private val RPM_AND_SPEED_LENGTHS = mapOf(0x0C to 2, 0x0D to 1)
internal const val MAX_RECONNECTS = 10

/** Errors that another attempt cannot fix without the rider's intervention. */
private val PERMANENT_ERRORS = setOf(
    DiagnosticError.PERMISSION_DENIED,
    DiagnosticError.BLUETOOTH_DISABLED,
    DiagnosticError.LOG_WRITE_FAILED,
)

/** 1 s, 2 s, 4 s, 8 s, then every 10 s. */
internal fun reconnectBackoffMillis(attempt: Int): Long =
    (1_000L shl (attempt - 1).coerceIn(0, 4)).coerceAtMost(10_000L)

internal class ElmDiagnosticSession(
    private val scope: CoroutineScope,
    private val transport: ElmTransport,
    private val reconnectDelayMillis: (attempt: Int) -> Long = ::reconnectBackoffMillis,
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
    private var everLive = false
    private var reachedLive = false

    override suspend fun start(adapterId: String) {
        if (job?.isActive == true) return
        everLive = false
        mutableTrace.value = emptyList()
        stage = DiagnosticStage.BLUETOOTH
        pendingCommand = null
        mutableState.value = DiagnosticState.ConnectingBluetooth
        job = scope.launch {
            var endReason = "Session ended"
            try {
                sessionLog = startLog()
                var reconnects = 0
                while (true) {
                    reachedLive = false
                    val error = attempt(adapterId)
                    if (reachedLive) reconnects = 0
                    recordFailure(error)
                    // Only a session that already produced readings reconnects on its own.
                    // A first connection that fails is reported so the rider can fix the setup.
                    if (!everLive || error in PERMANENT_ERRORS || reconnects >= MAX_RECONNECTS) {
                        mutableState.value = DiagnosticState.Failed(stage, error)
                        break
                    }
                    reconnects++
                    val wait = reconnectDelayMillis(reconnects)
                    mutableState.value = DiagnosticState.Reconnecting(reconnects, error)
                    record("Reconnecting in ${wait / 1_000.0} s · attempt $reconnects of $MAX_RECONNECTS")
                    delay(wait)
                }
            } catch (_: ElmLogException) {
                loggingFailed()
            } catch (cancelled: CancellationException) {
                endReason = "Session cancelled or disconnected"
                throw cancelled
            } finally {
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

    /** Runs one connection until it fails. Log failures and cancellation propagate. */
    private suspend fun attempt(adapterId: String): DiagnosticError {
        stage = DiagnosticStage.BLUETOOTH
        pendingCommand = null
        return try {
            connectAndPoll(adapterId)
        } catch (error: ElmLogException) {
            throw error
        } catch (_: TimeoutCancellationException) {
            DiagnosticError.TIMEOUT
        } catch (error: ElmTransportException) {
            error.error
        } catch (_: SecurityException) {
            DiagnosticError.PERMISSION_DENIED
        } catch (_: IOException) {
            if (stage == DiagnosticStage.BLUETOOTH) DiagnosticError.ADAPTER_UNREACHABLE
            else DiagnosticError.CONNECTION_LOST
        } finally {
            transport.close()
        }
    }

    private suspend fun connectAndPoll(adapterId: String): Nothing {
        record("Opening direct Bluetooth link to ELM327")
        transport.open(adapterId)
        record("Bluetooth link connected")
        stage = DiagnosticStage.ADAPTER
        showProgress(DiagnosticState.WaitingForAdapter)
        exchange("ATZ", "Reset adapter")
        val identification = exchange("ATI", "Identify adapter")
        if (answer(identification) == null) invalidResponse()
        configure("ATE0", "Disable echo")
        configure("ATH0", "Disable headers")
        selectProtocol()

        stage = DiagnosticStage.ECU
        showProgress(DiagnosticState.WaitingForEcu)
        val support = exchange("0100", "Read supported PIDs")
        val supported = ObdPidDecoder.supportedPids(support) ?: run {
            // UNABLE TO CONNECT: the ELM327 found no protocol, usually because the ignition is off.
            val noData = support.substringBefore('>').lineSequence().map(String::trim)
                .any { it.equals("NO DATA", ignoreCase = true) || it.equals("UNABLE TO CONNECT", ignoreCase = true) }
            if (noData) throw ElmTransportException(DiagnosticError.NO_ECU_DATA)
            invalidResponse()
        }
        recordPidSupport(supported, 0x0C, "010C", "Engine speed")
        recordPidSupport(supported, 0x0D, "010D", "Vehicle speed")
        val protocol = answer(exchange("ATDP", "Read active protocol"))
        val advertised = discoverSupportedPids(supported)
        stage = DiagnosticStage.POLLING
        poll(protocol, advertised)
    }

    /** Keeps the reconnecting state visible until readings resume. */
    private fun showProgress(next: DiagnosticState) {
        if (mutableState.value !is DiagnosticState.Reconnecting) mutableState.value = next
    }

    override suspend fun stop() {
        job?.cancelAndJoin()
        job = null
        stage = DiagnosticStage.BLUETOOTH
        record("Session disconnected", pendingCommand)
        pendingCommand = null
        mutableState.value = DiagnosticState.Disconnected
    }

    /**
     * Searches automatically but tries ISO 15765-4 CAN 11/500 first, the protocol the reference
     * motorcycle uses. Adapters that reject `ATSPA6` fall back to a plain automatic search.
     */
    private suspend fun selectProtocol() {
        if (!isOk(exchange("ATSPA6", "Select protocol automatically, trying CAN 11/500 first"))) {
            configure("ATSP0", "Select protocol automatically")
        }
    }

    private fun isOk(response: String): Boolean =
        response.substringBefore('>').lineSequence().any { it.trim() == "OK" }

    private suspend fun configure(command: String, description: String) {
        if (!isOk(exchange(command, description))) invalidResponse()
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

    private suspend fun poll(protocol: String?, advertised: Set<Int>): Nothing {
        val sensors = ObdTelemetryCatalog.pids.filter { it.id in advertised }
        val speedSupported = 0x0D in advertised
        val rpmSupported = 0x0C in advertised
        var nextSensor = 0

        // Probe once whether the ECU answers RPM and speed in a single exchange.
        val probe = if (rpmSupported && speedSupported) readRpmAndSpeed() else null
        val combined = probe != null &&
            probe.first is SignalReading.Sample && probe.second is SignalReading.Sample
        if (probe != null) {
            record(if (combined) "Combined $RPM_AND_SPEED supported · polling RPM and speed together"
                else "Combined $RPM_AND_SPEED not supported · polling RPM and speed separately")
        }
        var live = if (combined) {
            DiagnosticState.Live(protocol, probe.first, probe.second, telemetry = TelemetrySnapshot(advertised))
        } else {
            DiagnosticState.Live(
                protocol, readRpm(rpmSupported), readSpeed(speedSupported),
                telemetry = TelemetrySnapshot(advertised),
            )
        }
        mutableState.value = live
        reachedLive = true
        everLive = true
        var nextSpeedAt =
            if (speedSupported && !combined) monotonicMillis() + SPEED_PERIOD_MS else Long.MAX_VALUE
        var nextSensorAt =
            if (sensors.isNotEmpty()) monotonicMillis() + OTHER_SENSOR_PERIOD_MS else Long.MAX_VALUE
        delay(PRIMARY_PERIOD_MS)

        // Exits only by exception: an exchange failure or cancellation during delay.
        while (true) {
            val cycleStartedAt = monotonicMillis()
            if (combined) {
                val (rpm, speed) = readRpmAndSpeed()
                live = live.copy(engineRpm = rpm, speedKmh = speed)
                mutableState.value = live
            } else if (rpmSupported) {
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

            delay((PRIMARY_PERIOD_MS - (monotonicMillis() - cycleStartedAt)).coerceAtLeast(0))
        }
    }

    private suspend fun readRpm(supported: Boolean): SignalReading =
        readSignal(supported, "010C", "Engine speed", "rpm", ObdPidDecoder::engineRpm)

    private suspend fun readSpeed(supported: Boolean): SignalReading =
        readSignal(supported, "010D", "Vehicle speed", "km/h") { ObdPidDecoder.speedKmh(it)?.toDouble() }

    private suspend fun readRpmAndSpeed(): Pair<SignalReading, SignalReading> {
        val response = transmit(RPM_AND_SPEED, "Engine and vehicle speed")
        val receivedAtMillis = monotonicMillis()
        val payloads = ObdPidDecoder.multiplePids(response, RPM_AND_SPEED_LENGTHS)
        val rpm = payloads[0x0C]?.let(ObdPidDecoder::engineRpm)
        val speed = payloads[0x0D]?.single()?.toDouble()
        record(
            "Engine speed · ${rpm ?: "unavailable"} rpm · Vehicle speed · ${speed ?: "unavailable"} km/h",
            RPM_AND_SPEED, response,
        )
        fun sample(value: Double?) =
            if (value == null) SignalReading.Unavailable else SignalReading.Sample(value, receivedAtMillis)
        return sample(rpm) to sample(speed)
    }

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

    private suspend fun recordFailure(error: DiagnosticError) {
        try {
            val partial = transport.takePartialResponse()
            record(error.name.replace('_', ' ').lowercase(), pendingCommand, partial, ElmLogEvent.ERROR)
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
