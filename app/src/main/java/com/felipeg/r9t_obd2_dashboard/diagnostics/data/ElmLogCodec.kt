package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticTraceEntry
import java.time.Instant
import java.time.format.DateTimeParseException
import org.json.JSONException
import org.json.JSONObject

/** One line of a session log: what happened, how long after the log started, and its trace entry. */
internal data class ElmLogRecord(
    val event: ElmLogEvent,
    val elapsedMillis: Long,
    val entry: DiagnosticTraceEntry,
)

/**
 * The JSON Lines format of ELM327 session logs, one object per line. Session logs write it and
 * capture replay reads it back, so both sides always agree on the fields.
 */
internal object ElmLogCodec {
    private const val TIMESTAMP = "timestamp"
    private const val ELAPSED_MS = "elapsedMs"
    private const val EVENT = "event"
    private const val STAGE = "stage"
    private const val DESCRIPTION = "description"
    private const val COMMAND = "command"
    private const val RESPONSE = "response"

    /** A missing command or response leaves its field out. */
    fun encode(record: ElmLogRecord): String = JSONObject()
        .put(TIMESTAMP, Instant.ofEpochMilli(record.entry.timestampMillis).toString())
        .put(ELAPSED_MS, record.elapsedMillis)
        .put(EVENT, record.event.name)
        .put(STAGE, record.entry.stage.name)
        .put(DESCRIPTION, record.entry.description)
        .put(COMMAND, record.entry.command)
        .put(RESPONSE, record.entry.response)
        .toString()

    /** @throws IllegalArgumentException when [line] is not a valid log line. */
    fun decode(line: String): ElmLogRecord = try {
        val json = JSONObject(line)
        ElmLogRecord(
            event = ElmLogEvent.valueOf(json.getString(EVENT)),
            elapsedMillis = json.getLong(ELAPSED_MS).also { require(it >= 0) { "Negative elapsed time" } },
            entry = DiagnosticTraceEntry(
                timestampMillis = Instant.parse(json.getString(TIMESTAMP)).toEpochMilli(),
                stage = DiagnosticStage.valueOf(json.getString(STAGE)),
                description = json.getString(DESCRIPTION),
                command = json.optString(COMMAND).ifEmpty { null },
                response = json.optString(RESPONSE).ifEmpty { null },
            ),
        )
    } catch (error: JSONException) {
        throw IllegalArgumentException("Malformed ELM327 log line", error)
    } catch (error: DateTimeParseException) {
        throw IllegalArgumentException("Malformed ELM327 log timestamp", error)
    }
}
