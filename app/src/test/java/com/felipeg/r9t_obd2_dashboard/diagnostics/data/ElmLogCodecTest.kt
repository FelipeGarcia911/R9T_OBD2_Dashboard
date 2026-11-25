package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticTraceEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ElmLogCodecTest {
    @Test
    fun decodesWhatItEncodes() {
        val records = listOf(
            ElmLogRecord(ElmLogEvent.EVENT, 0, DiagnosticTraceEntry(0, DiagnosticStage.BLUETOOTH, "Opening")),
            ElmLogRecord(
                ElmLogEvent.RX, 1_234,
                DiagnosticTraceEntry(1_700_000_000_123, DiagnosticStage.POLLING, "Engine speed", "010C", "41 0C 1A F8\r>"),
            ),
            ElmLogRecord(ElmLogEvent.SESSION_END, 5_000, DiagnosticTraceEntry(5_000, DiagnosticStage.ECU, "Session ended")),
        )
        records.forEach { record -> assertEquals(record, ElmLogCodec.decode(ElmLogCodec.encode(record))) }
    }

    @Test
    fun readsLinesWrittenByEarlierBuilds() {
        val line = """{"timestamp":"2026-01-01T00:00:01.600Z","elapsedMs":1600,"event":"RX","stage":"POLLING",""" +
            """"description":"Engine speed · 1100 rpm","command":"010C","response":"41 0C 11 30\r>"}"""
        val record = ElmLogCodec.decode(line)

        assertEquals(ElmLogEvent.RX, record.event)
        assertEquals(1_600L, record.elapsedMillis)
        assertEquals(
            DiagnosticTraceEntry(1_767_225_601_600, DiagnosticStage.POLLING, "Engine speed · 1100 rpm", "010C", "41 0C 11 30\r>"),
            record.entry,
        )
    }

    @Test
    fun rejectsMalformedLinesWithOneExceptionType() {
        listOf(
            "not json",
            """{"timestamp":"yesterday","elapsedMs":0,"event":"EVENT","stage":"ECU","description":"x"}""",
            """{"timestamp":"2026-01-01T00:00:00Z","elapsedMs":-1,"event":"EVENT","stage":"ECU","description":"x"}""",
            """{"timestamp":"2026-01-01T00:00:00Z","elapsedMs":0,"event":"UNKNOWN","stage":"ECU","description":"x"}""",
            """{"timestamp":"2026-01-01T00:00:00Z","elapsedMs":0,"event":"EVENT","stage":"ECU"}""",
        ).forEach { line ->
            assertThrows(line, IllegalArgumentException::class.java) { ElmLogCodec.decode(line) }
        }
    }
}
