package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the polling schedule and trace retention before ElmDiagnosticSession is split up.
 * RPM and speed own the bus; secondary sensors get at most one slot per second, vitals first.
 */
class ElmPollingCharacterizationTest {
    @Test
    fun separateModeSendsAtMostOneSecondaryBetweenRpmReads() = runBlocking {
        val transport = ScriptedTransport { command ->
            when (command) {
                "0100" -> ADVERTISES_LOAD_COOLANT_RPM_SPEED
                "ATDP" -> "ISO 15765-4 (CAN 11/500)\r>"
                "010C0D" -> "NO DATA\r>"
                "010C" -> "41 0C 1A F8\r>"
                "010D" -> "41 0D 20\r>"
                "0104" -> "41 04 80\r>"
                "0105" -> "41 05 7B\r>"
                else -> initReply(command)
            }
        }
        val session = ElmDiagnosticSession(this, transport)
        val startedAt = System.nanoTime()
        try {
            session.start("paired-device")
            delay(3_300)
        } finally {
            session.stop()
        }
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        val polling = transport.commands.dropWhile { it != "010C0D1" }
        assertEquals(
            "Probes the combined request with and without a single-reply count, then reads both signals",
            listOf("010C0D1", "010C0D", "010C", "010D"), polling.take(4),
        )
        val loop = polling.drop(4)
        assertEquals("Each cycle starts with RPM", "010C", loop.first())
        loop.forEachIndexed { index, command ->
            if (command != "010C") {
                assertEquals("$command at $index must follow an RPM read: $loop", "010C", loop[index - 1])
            }
        }
        assertFalse("Combined request is not retried after the probe fails", "010C0D" in loop || "010C0D1" in loop)

        val speedReads = loop.count { it == "010D" }
        assertTrue("Speed refreshes about every 250 ms, got $speedReads in $elapsedMillis ms", speedReads >= 4)

        val secondaries = loop.filter { it == "0104" || it == "0105" }
        assertTrue("At least one secondary in $elapsedMillis ms", secondaries.isNotEmpty())
        assertTrue(
            "At most one secondary every $SECONDARY_PERIOD_MS ms, got ${secondaries.size} in $elapsedMillis ms",
            secondaries.size <= elapsedMillis / SECONDARY_PERIOD_MS + 1,
        )
        assertEquals("Engine vitals are read before diagnostic-only PIDs", "0105", secondaries.first())
    }

    @Test
    fun combinedModeNeverPollsRpmOrSpeedOnTheirOwn() = runBlocking {
        val transport = ScriptedTransport { command ->
            when (command) {
                "0100" -> ADVERTISES_LOAD_COOLANT_RPM_SPEED
                "ATDP" -> "ISO 15765-4 (CAN 11/500)\r>"
                "010C0D" -> "41 0C 1A F8 0D 20\r>"
                "0104" -> "41 04 80\r>"
                "0105" -> "41 05 7B\r>"
                else -> initReply(command)
            }
        }
        val session = ElmDiagnosticSession(this, transport)
        val live: DiagnosticState.Live
        try {
            session.start("paired-device")
            delay(2_400)
            live = session.state.value as DiagnosticState.Live
        } finally {
            session.stop()
        }

        assertEquals("The single-reply probe comes first", "010C0D1", transport.commands.first { it.startsWith("010C0D") })
        val polling = transport.commands.dropWhile { it != "010C0D" }
        assertFalse("010C" in polling || "010D" in polling)
        polling.forEachIndexed { index, command ->
            if (command != "010C0D") {
                assertEquals("$command at $index must follow a combined read: $polling", "010C0D", polling[index - 1])
            }
        }

        assertEquals(1726.0, (live.engineRpm as SignalReading.Sample).value, 0.0)
        assertEquals(32.0, (live.speedKmh as SignalReading.Sample).value, 0.0)
        assertEquals(setOf(0x04, 0x05, 0x0C, 0x0D), live.telemetry.advertisedPids)
        val coolant = live.telemetry.readings.getValue(0x05)
        assertEquals(TelemetryStatus.NUMERIC, coolant.status)
        assertEquals(83.0, coolant.measurements.single().value, 0.0)
        assertEquals("7B", coolant.payload)
        assertFalse("Only one secondary slot has passed", 0x04 in live.telemetry.readings)
    }

    @Test
    fun anAdapterThatAcceptsSingleReplyCountsGetsThemOnEveryRequest() = runBlocking {
        val transport = ScriptedTransport { command ->
            when (command) {
                "0100" -> ADVERTISES_LOAD_COOLANT_RPM_SPEED
                "ATDP" -> "ISO 15765-4 (CAN 11/500)\r>"
                "010C0D1" -> "41 0C 1A F8 0D 20\r>"
                "01041" -> "41 04 80\r>"
                "01051" -> "41 05 7B\r>"
                else -> initReply(command)
            }
        }
        val session = ElmDiagnosticSession(this, transport)
        val live: DiagnosticState.Live
        try {
            session.start("paired-device")
            delay(2_400)
            live = session.state.value as DiagnosticState.Live
        } finally {
            session.stop()
        }

        val polling = transport.commands.dropWhile { it != "010C0D1" }
        assertTrue("A secondary read happened: $polling", polling.any { it == "01051" })
        assertEquals("Every polling request carries the count", emptyList<String>(), polling.filterNot { it.endsWith("1") })
        assertEquals(1726.0, (live.engineRpm as SignalReading.Sample).value, 0.0)
        assertEquals(32.0, (live.speedKmh as SignalReading.Sample).value, 0.0)
        assertEquals(83.0, live.telemetry.readings.getValue(0x05).measurements.single().value, 0.0)
        assertTrue(session.trace.value.any { it.description.endsWith("single-reply requests") })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fullTraceDropsTheOldestReadingsAndKeepsTheHandshake() = runTest {
        var rpmReads = 0
        val transport = ScriptedTransport { command ->
            when (command) {
                "0100" -> "41 00 00 10 00 00\r>" // RPM only: one reading per cycle, nothing else.
                "ATDP" -> "ISO 15765-4 (CAN 11/500)\r>"
                "010C" -> rpmResponse(++rpmReads)
                else -> initReply(command)
            }
        }
        val session = ElmDiagnosticSession(backgroundScope, transport)
        session.start("paired-device")
        advanceTimeBy(10_000)
        val trace = session.trace.value
        session.stop()

        assertTrue("Needs more readings than the trace holds, got $rpmReads", rpmReads > 150)
        assertEquals(120, trace.size)
        listOf("ATZ", "ATI", "ATE0", "ATH0", "ATS0", "ATRV", "ATSPA6", "0100", "ATDP").forEach { command ->
            assertTrue("Handshake entry $command was evicted", trace.any { it.command == command })
        }
        val readings = trace.filter { it.stage == DiagnosticStage.POLLING && it.response != null }
        assertEquals("The newest entry is the latest reading", readings.last(), trace.last())
        assertEquals(
            "Only the most recent readings remain, in order",
            ((rpmReads - readings.size + 1)..rpmReads).map(::rpmResponse),
            readings.map { it.response },
        )
    }

    private fun rpmResponse(rpm: Int): String {
        val raw = rpm * 4
        return "41 0C %02X %02X\r>".format(raw shr 8, raw and 0xFF)
    }

    private companion object {
        /** PIDs 0x04 (load), 0x05 (coolant), 0x0C (RPM) and 0x0D (speed). */
        const val ADVERTISES_LOAD_COOLANT_RPM_SPEED = "41 00 18 18 00 00\r>"
    }
}
