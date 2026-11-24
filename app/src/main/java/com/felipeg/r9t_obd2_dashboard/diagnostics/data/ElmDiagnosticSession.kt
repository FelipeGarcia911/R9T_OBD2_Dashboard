package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticSession
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticTraceEntry
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class ElmDiagnosticSession(
    private val scope: CoroutineScope,
    private val transport: ElmTransport,
) : DiagnosticSession {
    private val mutableState = MutableStateFlow<DiagnosticState>(DiagnosticState.Disconnected)
    private val mutableTrace = MutableStateFlow<List<DiagnosticTraceEntry>>(emptyList())
    override val state = mutableState.asStateFlow()
    override val trace = mutableTrace.asStateFlow()
    private var job: Job? = null
    private var stage = DiagnosticStage.BLUETOOTH
    private var pendingCommand: String? = null

    override suspend fun start(adapterId: String) {
        if (job?.isActive == true) return
        mutableTrace.value = emptyList()
        stage = DiagnosticStage.BLUETOOTH
        pendingCommand = null
        mutableState.value = DiagnosticState.ConnectingBluetooth
        job = scope.launch {
            try {
                record("Opening Bluetooth link")
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
                val protocol = answer(exchange("ATDP", "Read active protocol"))
                stage = DiagnosticStage.POLLING
                while (isActive) {
                    val rpm = readSignal(0x0C in supported, "010C", "Engine speed", "rpm",
                        ObdPidDecoder::engineRpm)
                    val speed = readSignal(0x0D in supported, "010D", "Vehicle speed", "km/h") {
                        ObdPidDecoder.speedKmh(it)?.toDouble()
                    }
                    mutableState.value = DiagnosticState.Live(protocol, rpm, speed)
                    delay(250)
                }
            } catch (_: TimeoutCancellationException) {
                fail(DiagnosticError.TIMEOUT)
            } catch (error: ElmTransportException) {
                fail(error.error)
            } catch (_: SecurityException) {
                fail(DiagnosticError.PERMISSION_DENIED)
            } catch (_: IOException) {
                fail(if (stage == DiagnosticStage.BLUETOOTH) DiagnosticError.ADAPTER_UNREACHABLE
                    else DiagnosticError.CONNECTION_LOST)
            } finally {
                transport.close()
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
        pendingCommand = command
        val response = transport.exchange(command)
        pendingCommand = null
        record(description, command, response)
        return response
    }

    private suspend fun readSignal(
        supported: Boolean,
        command: String,
        label: String,
        unit: String,
        decode: (String) -> Double?,
    ): SignalReading {
        if (!supported) return SignalReading.Unavailable
        pendingCommand = command
        val response = transport.exchange(command)
        pendingCommand = null
        val value = decode(response)
        record(if (value == null) "$label unavailable" else "$label · $value $unit", command, response)
        return if (value == null) SignalReading.Unavailable
        else SignalReading.Sample(value, System.nanoTime() / 1_000_000)
    }

    private fun answer(response: String): String? =
        response.substringBefore('>').lineSequence().map(String::trim)
            .firstOrNull { it.isNotEmpty() && it !in setOf("ATI", "ATDP", "?", "ERROR", "NO DATA") }

    private fun invalidResponse(): Nothing = throw ElmTransportException(DiagnosticError.INVALID_RESPONSE)

    private fun fail(error: DiagnosticError) {
        record(error.name.replace('_', ' ').lowercase(), pendingCommand)
        pendingCommand = null
        mutableState.value = DiagnosticState.Failed(stage, error)
    }

    private fun record(description: String, command: String? = null, response: String? = null) {
        val entry = DiagnosticTraceEntry(System.currentTimeMillis(), stage, description, command, response)
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
