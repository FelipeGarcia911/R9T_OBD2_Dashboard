package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticTraceEntry
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

internal const val TRACE_CAPACITY = 120

/**
 * Appends [entry] keeping at most [capacity] entries. When full it drops the oldest ECU reading
 * first, so the handshake that explains a session stays visible while readings stream in.
 */
internal fun List<DiagnosticTraceEntry>.appendCapped(
    entry: DiagnosticTraceEntry,
    capacity: Int = TRACE_CAPACITY,
): List<DiagnosticTraceEntry> {
    val entries = this + entry
    val oldestReading = entries.indexOfFirst { it.stage == DiagnosticStage.POLLING && it.response != null }
    return if (entries.size > capacity && oldestReading >= 0) {
        entries.toMutableList().apply { removeAt(oldestReading) }
    } else {
        entries.takeLast(capacity)
    }
}

/**
 * Writes down what a session does: the trace the diagnostics screen shows and, when enabled, the
 * detailed [log]. Each entry carries the [stage] the session is in when it is recorded.
 */
internal class SessionRecorder {
    private val mutableTrace = MutableStateFlow<List<DiagnosticTraceEntry>>(emptyList())
    val trace: StateFlow<List<DiagnosticTraceEntry>> = mutableTrace.asStateFlow()
    var stage = DiagnosticStage.BLUETOOTH
    var log: ElmSessionLog? = null

    fun clearTrace() {
        mutableTrace.value = emptyList()
    }

    /** Logs [command] before it is sent, so a reply that never arrives still shows what was asked. */
    suspend fun sending(command: String, description: String) {
        log?.append(
            ElmLogEvent.TX_ATTEMPT,
            DiagnosticTraceEntry(System.currentTimeMillis(), stage, description, command),
        )
    }

    suspend fun record(
        description: String,
        command: String? = null,
        response: String? = null,
        event: ElmLogEvent = if (response == null) ElmLogEvent.EVENT else ElmLogEvent.RX,
    ) {
        val entry = DiagnosticTraceEntry(System.currentTimeMillis(), stage, description, command, response)
        log?.let { log ->
            withContext(NonCancellable) { log.append(event, entry) }
        }
        if (event == ElmLogEvent.SESSION_END) return
        mutableTrace.value = mutableTrace.value.appendCapped(entry)
    }
}
