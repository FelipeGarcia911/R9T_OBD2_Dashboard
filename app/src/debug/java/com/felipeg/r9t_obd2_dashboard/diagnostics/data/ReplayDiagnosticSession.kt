package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import android.content.Context
import android.net.Uri
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticSession
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticTraceEntry
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plays an ELM327 session log back with its original timing, turning the recorded replies into
 * the states a live session would have shown: RPM, speed and the engine vitals. The adapter ID is
 * the capture's content URI, or [SAMPLE_CAPTURE] for the bundled excerpt of a real session on the
 * reference bike, whose readings repeat in a loop so the dashboard can be watched as on the bike.
 */
internal class ReplayDiagnosticSession(
    private val scope: CoroutineScope,
    private val context: Context,
) : DiagnosticSession {
    private val mutableState = MutableStateFlow<DiagnosticState>(DiagnosticState.Disconnected)
    private val mutableTrace = MutableStateFlow<List<DiagnosticTraceEntry>>(emptyList())
    override val state = mutableState.asStateFlow()
    override val trace = mutableTrace.asStateFlow()
    private var playback: Job? = null

    override suspend fun start(adapterId: String) {
        if (playback?.isActive == true) return
        mutableTrace.value = emptyList()
        mutableState.value = DiagnosticState.ConnectingBluetooth
        playback = scope.launch {
            try {
                play(load(adapterId), loop = adapterId == SAMPLE_CAPTURE)
            } catch (_: IOException) {
                invalidCapture()
            } catch (_: SecurityException) {
                invalidCapture()
            } catch (_: IllegalArgumentException) {
                invalidCapture()
            }
        }
    }

    override suspend fun stop() {
        playback?.cancelAndJoin()
        playback = null
        mutableState.value = DiagnosticState.Disconnected
    }

    private suspend fun load(source: String): List<ElmLogRecord> = withContext(Dispatchers.IO) {
        val input = if (source == SAMPLE_CAPTURE) context.assets.open("example-elm327.jsonl")
        else context.contentResolver.openInputStream(Uri.parse(source))
            ?: throw IOException("Cannot read capture")
        input.bufferedReader().useLines { lines ->
            lines.filter(String::isNotBlank).take(MAX_ENTRIES + 1).map(ElmLogCodec::decode).toList().also {
                require(it.isNotEmpty() && it.size <= MAX_ENTRIES)
            }
        }
    }

    /**
     * Plays [records] with their recorded timing. With [loop], the readings after the handshake
     * play again and again, as if the engine kept running through the same stretch.
     */
    private suspend fun play(records: List<ElmLogRecord>, loop: Boolean = false) {
        records.zipWithNext().forEach { (a, b) ->
            require(b.elapsedMillis >= a.elapsedMillis) { "Capture goes back in time" }
        }
        var live = playOnce(records, NO_READINGS)
        val readings = records.dropWhile { it.entry.stage != DiagnosticStage.POLLING }
        while (loop && readings.size > 1) live = playOnce(readings, live)
        if (mutableState.value !is DiagnosticState.Failed) mutableState.value = DiagnosticState.Disconnected
    }

    private suspend fun playOnce(records: List<ElmLogRecord>, from: DiagnosticState.Live): DiagnosticState.Live {
        val firstElapsed = records.first().elapsedMillis
        val startedAt = System.nanoTime()
        var live = from
        for (record in records) {
            val playbackTime = (System.nanoTime() - startedAt) / 1_000_000
            delay((record.elapsedMillis - firstElapsed - playbackTime).coerceAtLeast(0))
            if (record.event != ElmLogEvent.TX_ATTEMPT) {
                mutableTrace.value = mutableTrace.value.appendCapped(record.entry)
            }
            live = apply(record, live)
        }
        // Keep each pass's gap between the last and first reading, so the loop does not stutter.
        delay(LOOP_GAP_MILLIS)
        return live
    }

    /** Shows the state [record] leads to and returns the readings to build on next. */
    private fun apply(record: ElmLogRecord, live: DiagnosticState.Live): DiagnosticState.Live {
        val entry = record.entry
        return when (record.event) {
            ElmLogEvent.TX_ATTEMPT -> live
            ElmLogEvent.EVENT -> live.also {
                if (entry.stage == DiagnosticStage.BLUETOOTH && entry.description == "Bluetooth link connected") {
                    mutableState.value = DiagnosticState.WaitingForAdapter
                }
            }
            ElmLogEvent.RX -> applyReply(entry.command, requireNotNull(entry.response), record.elapsedMillis, live)
            ElmLogEvent.ERROR -> {
                val error = DiagnosticError.entries.firstOrNull {
                    it.name.replace('_', ' ').equals(entry.description, ignoreCase = true)
                } ?: DiagnosticError.INVALID_RESPONSE
                mutableState.value = DiagnosticState.Failed(entry.stage, error)
                // A capture can continue with a reconnection; never show readings from before it.
                NO_READINGS
            }
            ElmLogEvent.SESSION_END -> live.also {
                if (mutableState.value !is DiagnosticState.Failed) mutableState.value = DiagnosticState.Disconnected
            }
        }
    }

    private fun applyReply(
        command: String?,
        response: String,
        receivedAtMillis: Long,
        live: DiagnosticState.Live,
    ): DiagnosticState.Live {
        fun reading(value: Double?): SignalReading =
            value?.let { SignalReading.Sample(it, receivedAtMillis) } ?: SignalReading.Unavailable

        // Captures may carry single-reply counts, such as 010C0D1.
        val next = when (command?.let(ObdPids::baseRequest)) {
            "ATI" -> {
                mutableState.value = DiagnosticState.WaitingForEcu
                return live
            }
            // Same reading of the reply as the live session, so replays show its protocol.
            "ATDP" -> live.copy(protocol = ElmResponse(response).answer)
            RPM_REQUEST -> live.copy(engineRpm = reading(ObdPidDecoder.engineRpm(response)))
            SPEED_REQUEST -> live.copy(speedKmh = reading(ObdPidDecoder.speedKmh(response)?.toDouble()))
            ObdPids.RPM_AND_SPEED -> {
                val (rpm, speed) = ObdPidDecoder.rpmAndSpeed(response)
                live.copy(engineRpm = reading(rpm), speedKmh = reading(speed))
            }
            else -> telemetryPid(command)?.let { pid ->
                val reading = ObdTelemetryCatalog.read(pid, response, receivedAtMillis)
                live.copy(telemetry = live.telemetry.copy(readings = live.telemetry.readings + (pid.id to reading)))
            } ?: return live
        }
        mutableState.value = next
        return next
    }

    /** The engine vitals and other Mode 01 sensors a capture may contain. */
    private fun telemetryPid(command: String?): ObdTelemetryPid? {
        val base = command?.let(ObdPids::baseRequest) ?: return null
        return ObdTelemetryCatalog.pids.firstOrNull { it.command == base }
    }

    private fun invalidCapture() {
        val entry = DiagnosticTraceEntry(
            System.currentTimeMillis(), DiagnosticStage.BLUETOOTH,
            "Could not read this ELM327 JSON Lines capture",
        )
        mutableTrace.value = mutableTrace.value.appendCapped(entry)
        mutableState.value = DiagnosticState.Failed(DiagnosticStage.BLUETOOTH, DiagnosticError.INVALID_RESPONSE)
    }

    companion object {
        const val SAMPLE_CAPTURE = "sample"
        private const val MAX_ENTRIES = 50_000
        private const val LOOP_GAP_MILLIS = 66L
        private val RPM_REQUEST = ObdPids.request(ObdPids.ENGINE_RPM)
        private val SPEED_REQUEST = ObdPids.request(ObdPids.VEHICLE_SPEED)
        private val NO_READINGS = DiagnosticState.Live(null, SignalReading.Unavailable, SignalReading.Unavailable)
    }
}
