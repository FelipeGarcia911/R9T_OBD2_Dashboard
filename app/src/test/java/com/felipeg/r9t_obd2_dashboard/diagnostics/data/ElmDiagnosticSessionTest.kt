package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticTraceEntry
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmDiagnosticSessionTest {
    @Test
    fun identifiesAdapterAndEcuThenReadsOnlySupportedSignals() = runBlocking {
        lateinit var session: ElmDiagnosticSession
        val transport = ScriptedTransport { command ->
            when (command) {
                "ATI" -> {
                    assertEquals(DiagnosticState.WaitingForAdapter, session.state.value)
                    initReply(command)
                }
                "0100" -> {
                    assertEquals(DiagnosticState.WaitingForEcu, session.state.value)
                    "SEARCHING...\r41 00 00 10 00 00\r>"
                }
                "ATDP" -> "AUTO, ISO 15765-4 (CAN)\r>"
                "010C" -> "41 0C 1A F8\r>"
                else -> initReply(command)
            }
        }
        session = ElmDiagnosticSession(this, transport)
        try {
            session.start("paired-device")
            val live = withTimeout(2_000) { session.state.filterIsInstance<DiagnosticState.Live>().first() }
            assertEquals(1726.0, (live.engineRpm as SignalReading.Sample).value, 0.0)
            assertEquals(SignalReading.Unavailable, live.speedKmh)
            assertEquals("AUTO, ISO 15765-4 (CAN)", live.protocol)
            assertTrue(session.trace.value.any {
                it.description == "Vehicle speed (010D) · not advertised by the ECU" && it.command == null
            })
            assertEquals(listOf("ATZ", "ATI", "ATE0", "ATH0", "ATSP0", "0100", "ATDP", "010C"),
                transport.commands)
            assertTrue(session.trace.value.any { it.command == "010C" && it.response == "41 0C 1A F8\r>" })
        } finally {
            session.stop()
        }
        assertTrue(transport.closed)
        assertEquals(DiagnosticState.Disconnected, session.state.value)
    }

    @Test
    fun recordsThePendingCommandWhenTheEcuTimesOut() = runBlocking {
        val transport = ScriptedTransport { command ->
            if (command == "0100") withTimeout(25) { awaitCancellation() } else initReply(command)
        }
        val session = ElmDiagnosticSession(this, transport)
        try {
            session.start("paired-device")
            val failure = withTimeout(2_000) { session.state.filterIsInstance<DiagnosticState.Failed>().first() }
            assertEquals(DiagnosticState.Failed(DiagnosticStage.ECU, DiagnosticError.TIMEOUT), failure)
            assertEquals("0100", session.trace.value.last().command)
        } finally {
            session.stop()
        }
        assertTrue(transport.closed)
    }

    @Test
    fun distinguishesNoEcuDataFromAnExchangeTimeout() = runBlocking {
        val transport = ScriptedTransport { command ->
            if (command == "0100") "SEARCHING...\rNO DATA\r>" else initReply(command)
        }
        val session = ElmDiagnosticSession(this, transport)
        try {
            session.start("paired-device")
            val failure = withTimeout(2_000) {
                session.state.filterIsInstance<DiagnosticState.Failed>().first()
            }
            assertEquals(DiagnosticState.Failed(DiagnosticStage.ECU, DiagnosticError.NO_ECU_DATA), failure)
            assertTrue(session.trace.value.any {
                it.command == "0100" && it.response?.contains("NO DATA") == true
            })
        } finally {
            session.stop()
        }
    }

    @Test
    fun stoppingAnInFlightRequestClosesTheTransport() = runBlocking {
        val transport = ScriptedTransport { command ->
            if (command == "0100") awaitCancellation() else initReply(command)
        }
        val session = ElmDiagnosticSession(this, transport)
        try {
            session.start("paired-device")
            withTimeout(2_000) { session.state.first { it == DiagnosticState.WaitingForEcu } }
        } finally {
            session.stop()
        }
        assertTrue(transport.closed)
        assertEquals(DiagnosticState.Disconnected, session.state.value)
    }

    @Test
    fun savesEveryCommandBeforeSendingAndRecordsPartialTimeoutAndEnd() = runBlocking {
        val events = mutableListOf<Pair<ElmLogEvent, DiagnosticTraceEntry>>()
        val transport = ScriptedTransport(partialResponse = "SEARCHING...\r41 00") { command ->
            assertEquals(ElmLogEvent.TX_ATTEMPT, events.last().first)
            assertEquals(command, events.last().second.command)
            if (command == "0100") withTimeout(25) { awaitCancellation() } else initReply(command)
        }
        val session = ElmDiagnosticSession(this, transport) {
            ElmSessionLog { event, entry -> events.add(event to entry) }
        }
        try {
            session.start("paired-device")
            withTimeout(2_000) { session.state.filterIsInstance<DiagnosticState.Failed>().first() }
        } finally {
            session.stop()
        }
        assertEquals("ELM327 v1.5\r>", events.first { it.first == ElmLogEvent.RX }.second.response)
        val failure = events.single { it.first == ElmLogEvent.ERROR }.second
        assertEquals("0100", failure.command)
        assertEquals("SEARCHING...\r41 00", failure.response)
        assertEquals(ElmLogEvent.SESSION_END, events.last().first)
    }

    private fun initReply(command: String): String = when (command) {
        "ATZ", "ATI" -> "ELM327 v1.5\r>"
        "ATE0", "ATH0", "ATSP0" -> "OK\r>"
        else -> error("Unexpected command: $command")
    }

    private class ScriptedTransport(
        private var partialResponse: String? = null,
        private val reply: suspend (String) -> String,
    ) : ElmTransport {
        override fun takePartialResponse(): String? = partialResponse.also { partialResponse = null }
        val commands = mutableListOf<String>()
        var closed = false
        override suspend fun open(adapterId: String) = Unit
        override suspend fun exchange(command: String): String {
            commands += command
            return reply(command)
        }
        override fun close() {
            closed = true
        }
    }
}
