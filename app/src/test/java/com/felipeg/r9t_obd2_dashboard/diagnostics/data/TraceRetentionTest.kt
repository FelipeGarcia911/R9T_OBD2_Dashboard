package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticTraceEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class TraceRetentionTest {
    @Test
    fun keepsEverythingUntilFull() {
        val trace = listOf(event("a")).appendCapped(event("b"), capacity = 3)
        assertEquals(listOf("a", "b"), trace.map { it.description })
    }

    @Test
    fun dropsTheOldestReadingBeforeAnyHandshakeEntry() {
        val trace = listOf(event("open"), reading("r1"), event("note"), reading("r2"))
            .appendCapped(reading("r3"), capacity = 4)
        assertEquals(listOf("open", "note", "r2", "r3"), trace.map { it.description })
    }

    @Test
    fun pollingEventsWithoutAReplyAreNotReadings() {
        val pollingNote = DiagnosticTraceEntry(0, DiagnosticStage.POLLING, "Combined 010C0D supported")
        val trace = listOf(pollingNote, reading("r1")).appendCapped(reading("r2"), capacity = 2)
        assertEquals(listOf("Combined 010C0D supported", "r2"), trace.map { it.description })
    }

    @Test
    fun dropsTheOldestEntryWhenThereIsNoReadingLeft() {
        val trace = listOf(event("a"), event("b")).appendCapped(event("c"), capacity = 2)
        assertEquals(listOf("b", "c"), trace.map { it.description })
    }

    private fun event(description: String) = DiagnosticTraceEntry(0, DiagnosticStage.ADAPTER, description)

    private fun reading(description: String) =
        DiagnosticTraceEntry(0, DiagnosticStage.POLLING, description, "010C", "41 0C 1A F8\r>")
}
