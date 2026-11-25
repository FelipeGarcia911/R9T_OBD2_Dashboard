package com.felipeg.r9t_obd2_dashboard.diagnostics.ui

import android.content.Context
import android.net.Uri
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticSession
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticTraceEntry
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.ElmLogEvent
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.ObdPidDecoder
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

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
                play(load(adapterId))
            } catch (_: IOException) {
                invalidCapture()
            } catch (_: SecurityException) {
                invalidCapture()
            } catch (_: IllegalArgumentException) {
                invalidCapture()
            } catch (_: DateTimeParseException) {
                invalidCapture()
            } catch (_: JSONException) {
                invalidCapture()
            }
        }
    }

    override suspend fun stop() {
        playback?.cancelAndJoin()
        playback = null
        mutableState.value = DiagnosticState.Disconnected
    }

    private suspend fun load(source: String): List<ReplayEntry> = withContext(Dispatchers.IO) {
        val input = if (source == SAMPLE_CAPTURE) context.assets.open("example-elm327.jsonl")
        else context.contentResolver.openInputStream(Uri.parse(source))
            ?: throw IOException("Cannot read capture")
        input.bufferedReader().useLines { lines ->
            lines.filter(String::isNotBlank).take(MAX_ENTRIES + 1).map(::parse).toList().also {
                require(it.isNotEmpty() && it.size <= MAX_ENTRIES)
            }
        }
    }

    private suspend fun play(entries: List<ReplayEntry>) {
        val firstTimestamp = entries.first().elapsedMillis
        val startedAt = System.nanoTime()
        var previousTime = firstTimestamp
        var live = DiagnosticState.Live(null, SignalReading.Unavailable, SignalReading.Unavailable)
        for (entry in entries) {
            require(entry.elapsedMillis >= previousTime)
            val targetTime = entry.elapsedMillis - firstTimestamp
            val playbackTime = (System.nanoTime() - startedAt) / 1_000_000
            delay((targetTime - playbackTime).coerceAtLeast(0))
            previousTime = entry.elapsedMillis
            if (entry.event != ElmLogEvent.TX_ATTEMPT) {
                mutableTrace.value = (mutableTrace.value + entry.trace).takeLast(120)
            }
            when (entry.event) {
                ElmLogEvent.TX_ATTEMPT -> Unit
                ElmLogEvent.EVENT -> when (entry.trace.stage) {
                    DiagnosticStage.BLUETOOTH -> if (entry.trace.description == "Bluetooth link connected") {
                        mutableState.value = DiagnosticState.WaitingForAdapter
                    }
                    else -> Unit
                }
                ElmLogEvent.RX -> {
                    val response = requireNotNull(entry.trace.response)
                    when (entry.trace.command) {
                        "ATI" -> mutableState.value = DiagnosticState.WaitingForEcu
                        "ATDP" -> {
                            val protocol = response.substringBefore('>').lineSequence()
                                .map(String::trim).firstOrNull { it.isNotEmpty() && it != "ATDP" }
                            live = live.copy(protocol = protocol)
                            mutableState.value = live
                        }
                        "010C" -> {
                            live = live.copy(engineRpm = reading(ObdPidDecoder.engineRpm(response), entry))
                            mutableState.value = live
                        }
                        "010D" -> {
                            live = live.copy(speedKmh = reading(ObdPidDecoder.speedKmh(response)?.toDouble(), entry))
                            mutableState.value = live
                        }
                        "010C0D" -> {
                            val payloads = ObdPidDecoder.multiplePids(response, mapOf(0x0C to 2, 0x0D to 1))
                            live = live.copy(
                                engineRpm = reading(payloads[0x0C]?.let(ObdPidDecoder::engineRpm), entry),
                                speedKmh = reading(payloads[0x0D]?.single()?.toDouble(), entry),
                            )
                            mutableState.value = live
                        }
                    }
                }
                ElmLogEvent.ERROR -> {
                    val error = DiagnosticError.entries.firstOrNull {
                        it.name.replace('_', ' ').equals(entry.trace.description, ignoreCase = true)
                    } ?: DiagnosticError.INVALID_RESPONSE
                    mutableState.value = DiagnosticState.Failed(entry.trace.stage, error)
                    // A capture can continue with a reconnection; never show readings from before it.
                    live = DiagnosticState.Live(null, SignalReading.Unavailable, SignalReading.Unavailable)
                }
                ElmLogEvent.SESSION_END -> if (mutableState.value !is DiagnosticState.Failed) {
                    mutableState.value = DiagnosticState.Disconnected
                }
            }
        }
        if (mutableState.value !is DiagnosticState.Failed) mutableState.value = DiagnosticState.Disconnected
    }

    private fun invalidCapture() {
        val entry = DiagnosticTraceEntry(
            System.currentTimeMillis(), DiagnosticStage.BLUETOOTH,
            "Could not read this ELM327 JSON Lines capture",
        )
        mutableTrace.value = mutableTrace.value.takeLast(119) + entry
        mutableState.value = DiagnosticState.Failed(DiagnosticStage.BLUETOOTH, DiagnosticError.INVALID_RESPONSE)
    }

    private fun reading(value: Double?, entry: ReplayEntry): SignalReading =
        value?.let { SignalReading.Sample(it, entry.elapsedMillis) } ?: SignalReading.Unavailable

    private fun parse(line: String): ReplayEntry {
        val json = JSONObject(line)
        return ReplayEntry(
            event = ElmLogEvent.valueOf(json.getString("event")),
            elapsedMillis = json.getLong("elapsedMs").also { require(it >= 0) },
            trace = DiagnosticTraceEntry(
                timestampMillis = Instant.parse(json.getString("timestamp")).toEpochMilli(),
                stage = DiagnosticStage.valueOf(json.getString("stage")),
                description = json.getString("description"),
                command = json.optString("command").ifEmpty { null },
                response = json.optString("response").ifEmpty { null },
            ),
        )
    }

    private data class ReplayEntry(val event: ElmLogEvent, val elapsedMillis: Long, val trace: DiagnosticTraceEntry)

    companion object {
        const val SAMPLE_CAPTURE = "sample"
        private const val MAX_ENTRIES = 50_000
    }
}
