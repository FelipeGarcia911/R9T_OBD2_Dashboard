package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticTraceEntry
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
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
            assertEquals(listOf("ATZ", "ATI", "ATE0", "ATH0", "ATS0", "ATRV", "ATSPA6", "0100", "ATDP", "010C"),
                transport.commands)
            assertTrue(session.trace.value.any { it.command == "010C" && it.response == "41 0C 1A F8\r>" })
            assertTrue(
                "Battery voltage is recorded for the log",
                session.trace.value.any { it.command == "ATRV" && it.response == "12.6V\r>" },
            )
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

    @Test
    fun followsAdvertisedPagesAndCapturesDashboardCandidates() = runBlocking {
        val transport = ScriptedTransport { command ->
            when (command) {
                "0100" -> "41 00 00 18 00 01\r>"
                "0120", "0140", "0160", "0180" ->
                    "41 ${command.takeLast(2)} 00 00 00 01\r>"
                "01A0" -> "41 A0 14 00 00 00\r>"
                "01A4" -> "41 A4 01 00 00 00\r>"
                "01A6" -> "41 A6 00 01 00 00\r>"
                "010C0D" -> "41 0C 1C 9E 0D 00\r>"
                "ATDP" -> "AUTO, ISO 15765-4 (CAN 11/500)\r>"
                else -> initReply(command)
            }
        }
        val session = ElmDiagnosticSession(this, transport)
        try {
            session.start("paired-device")
            val live = withTimeout(2_000) {
                session.state.filterIsInstance<DiagnosticState.Live>().first()
            }
            assertEquals(
                listOf("0120", "0140", "0160", "0180", "01A0", "01A4", "01A6", "010C0D1", "010C0D"),
                transport.commands.takeLast(9),
            )
            assertTrue(session.trace.value.any { it.command == "01A4" && it.response != null })
            assertTrue(session.trace.value.any { it.command == "01A6" && it.response != null })
            assertEquals(null, live.gear)
            assertEquals(SignalReading.Unavailable, live.odometerKm)
        } finally {
            session.stop()
        }
    }

    @Test
    fun publishesRpmBeforeASecondarySensorFinishes() = runBlocking {
        var rpmRequests = 0
        val transport = ScriptedTransport { command ->
            when (command) {
                "0100" -> "41 00 08 18 00 00\r>"
                "010C" -> if (++rpmRequests == 1) "41 0C 10 00\r>" else "41 0C 20 00\r>"
                "010D" -> "41 0D 00\r>"
                "010C0D" -> "NO DATA\r>"
                "0105" -> awaitCancellation()
                "ATDP" -> "ISO 15765-4\r>"
                else -> initReply(command)
            }
        }
        val session = ElmDiagnosticSession(this, transport)
        try {
            session.start("paired-device")
            val latest = withTimeout(2_000) {
                session.state.filterIsInstance<DiagnosticState.Live>().first { state ->
                    (state.engineRpm as? SignalReading.Sample)?.value == 2048.0
                }
            }
            assertEquals(0.0, (latest.speedKmh as SignalReading.Sample).value, 0.0)
            assertTrue(latest.telemetry.readings.isEmpty())
        } finally {
            session.stop()
        }
    }

    @Test
    fun reconnectsOnItsOwnAfterALiveLinkDrops() = runBlocking {
        lateinit var transport: ScriptedTransport
        var rpmRequests = 0
        transport = ScriptedTransport { command ->
            when (command) {
                "0100" -> "41 00 00 10 00 00\r>"
                "ATDP" -> "ISO 15765-4\r>"
                "010C" -> if (transport.opens == 1 && ++rpmRequests > 1) {
                    throw IOException("Link dropped")
                } else "41 0C 1A F8\r>"
                else -> initReply(command)
            }
        }
        val session = ElmDiagnosticSession(this, transport, reconnectDelayMillis = { 10 })
        try {
            session.start("paired-device")
            val reconnecting = withTimeout(2_000) {
                session.state.filterIsInstance<DiagnosticState.Reconnecting>().first()
            }
            assertEquals(DiagnosticState.Reconnecting(1, DiagnosticError.CONNECTION_LOST), reconnecting)
            withTimeout(2_000) {
                session.state.first { it is DiagnosticState.Live && transport.opens == 2 }
            }
            assertTrue(session.trace.value.any { it.description.startsWith("Reconnecting in") })
        } finally {
            session.stop()
        }
        assertTrue(transport.closed)
        assertEquals(DiagnosticState.Disconnected, session.state.value)
    }

    @Test
    fun givesUpAfterTheReconnectLimit() = runBlocking {
        var rpmRequests = 0
        val transport = ScriptedTransport(onOpen = { attempt ->
            if (attempt > 1) throw IOException("Adapter out of range")
        }) { command ->
            when (command) {
                "0100" -> "41 00 00 10 00 00\r>"
                "ATDP" -> "ISO 15765-4\r>"
                "010C" -> if (++rpmRequests > 1) throw IOException("Link dropped") else "41 0C 1A F8\r>"
                else -> initReply(command)
            }
        }
        val session = ElmDiagnosticSession(this, transport, reconnectDelayMillis = { 0 })
        try {
            session.start("paired-device")
            val failure = withTimeout(2_000) {
                session.state.filterIsInstance<DiagnosticState.Failed>().first()
            }
            assertEquals(
                DiagnosticState.Failed(DiagnosticStage.BLUETOOTH, DiagnosticError.ADAPTER_UNREACHABLE),
                failure,
            )
            assertEquals(1 + MAX_RECONNECTS, transport.opens)
        } finally {
            session.stop()
        }
    }

    @Test
    fun anAdapterWithoutBatteryVoltageStillConnects() = runBlocking {
        val transport = ScriptedTransport { command ->
            when (command) {
                "ATRV" -> "?\r>"
                "0100" -> "41 00 00 10 00 00\r>"
                "ATDP" -> "ISO 15765-4\r>"
                "010C" -> "41 0C 1A F8\r>"
                else -> initReply(command)
            }
        }
        val session = ElmDiagnosticSession(this, transport)
        val live = try {
            session.start("paired-device")
            withTimeout(2_000) { session.state.filterIsInstance<DiagnosticState.Live>().first() }
        } finally {
            session.stop()
        }
        assertEquals(1726.0, (live.engineRpm as SignalReading.Sample).value, 0.0)
    }

    @Test
    fun stopsSendingRequestsOnceTheIgnitionIsSwitchedOff() = runBlocking {
        var rpmRequests = 0
        val transport = ScriptedTransport { command ->
            when (command) {
                "0100" -> "41 00 00 10 00 00\r>"
                "ATDP" -> "ISO 15765-4\r>"
                // Three readings, then the ECU goes quiet as when the key is turned off.
                "010C" -> if (++rpmRequests <= 3) "41 0C 1A F8\r>" else "NO DATA\r>"
                else -> initReply(command)
            }
        }
        val session = ElmDiagnosticSession(this, transport, reconnectDelayMillis = { 0 })
        try {
            session.start("paired-device")
            val failure = withTimeout(4_000) { session.state.filterIsInstance<DiagnosticState.Failed>().first() }
            assertEquals(DiagnosticState.Failed(DiagnosticStage.POLLING, DiagnosticError.NO_ECU_DATA), failure)

            val sent = transport.commands.size
            delay(500)
            assertEquals("No requests after the session stopped", sent, transport.commands.size)
            assertEquals("No reconnect to a switched-off bike", 1, transport.opens)
            assertTrue(session.trace.value.any { it.description.contains("Stopping requests") })
        } finally {
            session.stop()
        }
    }

    @Test
    fun reconnectBackoffDoublesUpToTenSeconds() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 10_000L, 10_000L),
            (1..6).map(::reconnectBackoffMillis))
    }

    @Test
    fun pollsRpmAndSpeedTogetherWhenTheEcuAcceptsTheCombinedRequest() = runBlocking {
        var requests = 0
        val transport = ScriptedTransport { command ->
            when (command) {
                "0100" -> "SEARCHING...\r41 00 00 18 00 00\r>"
                "ATDP" -> "AUTO, ISO 15765-4 (CAN 11/500)\r>"
                "010C0D" -> if (++requests < 3) "41 0C 1C 9E 0D 00 \r\r>" else "41 0C 20 00 0D 2A \r\r>"
                else -> initReply(command)
            }
        }
        val session = ElmDiagnosticSession(this, transport)
        try {
            session.start("paired-device")
            val latest = withTimeout(2_000) {
                session.state.filterIsInstance<DiagnosticState.Live>().first {
                    (it.speedKmh as? SignalReading.Sample)?.value == 42.0
                }
            }
            assertEquals(2048.0, (latest.engineRpm as SignalReading.Sample).value, 0.0)
            assertTrue(transport.commands.none { it == "010C" || it == "010D" })
            assertTrue(session.trace.value.any { it.description.startsWith("Combined 010C0D supported") })
        } finally {
            session.stop()
        }
    }

    @Test
    fun reportsUnableToConnectAsNoEcuData() = runBlocking {
        val transport = ScriptedTransport { command ->
            if (command == "0100") "SEARCHING...\rUNABLE TO CONNECT\r\r>" else initReply(command)
        }
        val session = ElmDiagnosticSession(this, transport)
        try {
            session.start("paired-device")
            val failure = withTimeout(2_000) {
                session.state.filterIsInstance<DiagnosticState.Failed>().first()
            }
            assertEquals(DiagnosticState.Failed(DiagnosticStage.ECU, DiagnosticError.NO_ECU_DATA), failure)
        } finally {
            session.stop()
        }
    }

    @Test
    fun fallsBackToPlainAutomaticSearchWhenTheAdapterRejectsAPreferredProtocol() = runBlocking {
        val transport = ScriptedTransport { command ->
            when (command) {
                "ATSPA6" -> "?\r\r>"
                "0100" -> "41 00 00 10 00 00\r>"
                "ATDP" -> "AUTO, ISO 15765-4 (CAN 11/500)\r>"
                "010C" -> "41 0C 1A F8\r>"
                else -> initReply(command)
            }
        }
        val session = ElmDiagnosticSession(this, transport)
        try {
            session.start("paired-device")
            withTimeout(2_000) { session.state.filterIsInstance<DiagnosticState.Live>().first() }
            assertEquals(listOf("ATSPA6", "ATSP0", "0100"),
                transport.commands.dropWhile { it != "ATSPA6" }.take(3))
        } finally {
            session.stop()
        }
    }
}
