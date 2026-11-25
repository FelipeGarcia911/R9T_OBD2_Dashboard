package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticTraceEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmCommandPolicyTest {
    @Test
    fun allowsEveryCommandTheSessionSends() {
        listOf(
            "ATZ", "ATI", "ATE0", "ATH0", "ATRV", "ATSPA6", "ATSP0", "ATDP",
            "0100", "0120", "01A0", "010C", "010D", "010C0D", "010C0D1", "01051", "015C1", "01A4", "01A6",
        ).forEach { assertTrue(it, ElmCommandPolicy.isAllowed(it)) }
    }

    @Test
    fun blocksAnythingThatCouldChangeTheBike() {
        listOf(
            "04", // clear trouble codes and reset readiness monitors
            "08", // test control
            "1003", // diagnostic session control
            "2701", // security access
            "2EF190", // write data by identifier
            "3101FF00", // routine control
            "3401", // request download (programming)
            "22F190", // manufacturer read: not needed, so not allowed either
            "ATSH7E0", // custom headers
            "ATMA", // listening is not part of the polling session
            "ATPP0ESV81", // writing adapter parameters
        ).forEach { assertFalse(it, ElmCommandPolicy.isAllowed(it)) }
    }

    @Test
    fun blocksMalformedMode01Requests() {
        listOf("01", "010", "01ZZ", "010c", "01 0C", "010C0D0", "0101020304050607", "").forEach {
            assertFalse("'$it'", ElmCommandPolicy.isAllowed(it))
        }
    }

    @Test
    fun aBlockedCommandNeverReachesTheAdapterOrTheLog() = runBlocking {
        val logged = mutableListOf<DiagnosticTraceEntry>()
        val recorder = SessionRecorder().apply { log = ElmSessionLog { _, entry -> logged += entry } }
        val transport = ScriptedTransport { error("Nothing may be sent") }
        val channel = ElmChannel(transport, recorder)

        val failure = assertThrows(ElmTransportException::class.java) {
            runBlocking { channel.exchange("04", "Clear trouble codes") }
        }

        assertEquals(DiagnosticError.COMMAND_BLOCKED, failure.error)
        assertTrue(transport.commands.isEmpty())
        assertTrue(logged.isEmpty())
        assertEquals(null, channel.pendingCommand)
        assertEquals(DiagnosticStage.BLUETOOTH, recorder.stage)
    }
}
