package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticTraceEntry
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins how captures replay, including the round trip from ElmSessionLogs, before the log format
 * moves into a shared codec. Runs on a device because replay reads through a real Context.
 */
@RunWith(AndroidJUnit4::class)
class ReplayDiagnosticSessionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.cacheDir, "replay-test-${System.nanoTime()}")

    @After
    fun deleteCaptures() {
        directory.deleteRecursively()
    }

    /** A short scripted capture (androidTestDebug assets) that walks through every stage. */
    @Test
    fun replaysACaptureThroughEveryConnectionStage() = runBlocking {
        val protocol = "ISO 15765-4 (CAN)"
        directory.mkdirs()
        val scripted = File(directory, "scripted.jsonl")
        InstrumentationRegistry.getInstrumentation().context.assets.open("scripted-elm327.jsonl").use { input ->
            scripted.outputStream().use { input.copyTo(it) }
        }
        val states = replay(Uri.fromFile(scripted).toString()) { it is DiagnosticState.Failed }

        fun live(rpm: SignalReading, speed: SignalReading) = DiagnosticState.Live(protocol, rpm, speed)
        val unavailable = SignalReading.Unavailable
        assertEquals(
            listOf(
                DiagnosticState.ConnectingBluetooth,
                DiagnosticState.WaitingForAdapter,
                DiagnosticState.WaitingForEcu,
                live(unavailable, unavailable),
                live(SignalReading.Sample(1_100.0, 1_600), unavailable),
                live(SignalReading.Sample(1_100.0, 1_600), SignalReading.Sample(0.0, 1_800)),
                live(SignalReading.Sample(4_000.0, 2_700), SignalReading.Sample(0.0, 1_800)),
                live(SignalReading.Sample(4_000.0, 2_700), SignalReading.Sample(54.0, 2_900)),
                live(SignalReading.Sample(4_000.0, 2_700), unavailable),
                DiagnosticState.Failed(DiagnosticStage.POLLING, DiagnosticError.TIMEOUT),
            ),
            states,
        )
    }

    /** The bundled excerpt of a real session: live readings and vitals, looping without ending. */
    @Test
    fun playsTheRealSampleCaptureInALoop() = runBlocking {
        val session = ReplayDiagnosticSession(this, context)
        try {
            session.start(ReplayDiagnosticSession.SAMPLE_CAPTURE)
            val live = withTimeout(10_000) {
                session.state.first { state ->
                    state is DiagnosticState.Live && state.engineRpm is SignalReading.Sample &&
                        state.telemetry.readings.isNotEmpty()
                }
            } as DiagnosticState.Live
            assertEquals("AUTO, ISO 15765-4 (CAN 11/500)", live.protocol)
            assertTrue((live.engineRpm as SignalReading.Sample).value in 900.0..3_500.0)
        } finally {
            session.stop()
        }
    }

    @Test
    fun replaysALogWrittenBySessionLogs() = runBlocking {
        val log = ElmSessionLogs(directory).start()
        // Gaps let the collector observe each state; replay keeps the recorded timing.
        suspend fun append(event: ElmLogEvent, entry: DiagnosticTraceEntry) {
            log.append(event, entry)
            delay(40)
        }
        append(ElmLogEvent.EVENT, entry(DiagnosticStage.BLUETOOTH, "Bluetooth link connected"))
        append(ElmLogEvent.TX_ATTEMPT, entry(DiagnosticStage.ADAPTER, "Identify adapter", "ATI"))
        append(ElmLogEvent.RX, entry(DiagnosticStage.ADAPTER, "Identify adapter", "ATI", "ELM327 v1.5\r>"))
        append(ElmLogEvent.RX, entry(DiagnosticStage.ECU, "Read active protocol", "ATDP", "ISO 15765-4 (CAN)\r>"))
        append(
            ElmLogEvent.RX,
            entry(DiagnosticStage.POLLING, "Engine and vehicle speed", "010C0D", "41 0C 1A F8 0D 20\r>"),
        )
        append(ElmLogEvent.SESSION_END, entry(DiagnosticStage.POLLING, "Session ended"))
        log.close()
        val capture = Uri.fromFile(directory.listFiles()!!.single()).toString()

        val states = replay(capture) { it is DiagnosticState.Disconnected }

        val live = states.filterIsInstance<DiagnosticState.Live>().last()
        assertEquals("ISO 15765-4 (CAN)", live.protocol)
        assertEquals(1_726.0, (live.engineRpm as SignalReading.Sample).value, 0.0)
        assertEquals(32.0, (live.speedKmh as SignalReading.Sample).value, 0.0)
        assertEquals(DiagnosticState.Disconnected, states.last())
    }

    /** Captures since 2026-11-25 carry the single-reply count on every polling request. */
    @Test
    fun replaysSingleReplyRequests() = runBlocking {
        val log = ElmSessionLogs(directory).start()
        suspend fun append(event: ElmLogEvent, entry: DiagnosticTraceEntry) {
            log.append(event, entry)
            delay(40)
        }
        append(ElmLogEvent.RX, entry(DiagnosticStage.ECU, "Read active protocol", "ATDP", "ISO 15765-4 (CAN)\r>"))
        append(
            ElmLogEvent.RX,
            entry(DiagnosticStage.POLLING, "Engine and vehicle speed", "010C0D1", "41 0C 12 24 0D 00\r>"),
        )
        append(ElmLogEvent.SESSION_END, entry(DiagnosticStage.POLLING, "Session ended"))
        log.close()
        val capture = Uri.fromFile(directory.listFiles()!!.single()).toString()

        val states = replay(capture) { it is DiagnosticState.Disconnected }

        val live = states.filterIsInstance<DiagnosticState.Live>().last()
        assertEquals(1_161.0, (live.engineRpm as SignalReading.Sample).value, 0.0)
        assertEquals(0.0, (live.speedKmh as SignalReading.Sample).value, 0.0)
    }

    @Test
    fun reportsACaptureThatIsNotJsonLines() = runBlocking {
        directory.mkdirs()
        val file = File(directory, "broken.jsonl").apply { writeText("not json\n") }
        val session = ReplayDiagnosticSession(this, context)

        session.start(Uri.fromFile(file).toString())
        val failed = withTimeout(5_000) { session.state.first { it is DiagnosticState.Failed } }
        session.stop()

        assertEquals(DiagnosticState.Failed(DiagnosticStage.BLUETOOTH, DiagnosticError.INVALID_RESPONSE), failed)
        assertEquals("Could not read this ELM327 JSON Lines capture", session.trace.value.last().description)
    }

    /** Plays [capture] and returns every state observed until [isDone] matches. */
    private suspend fun CoroutineScope.replay(
        capture: String,
        isDone: (DiagnosticState) -> Boolean,
    ): List<DiagnosticState> {
        val session = ReplayDiagnosticSession(this, context)
        val states = mutableListOf<DiagnosticState>()
        val collector = launch { session.state.collect { states += it } }
        try {
            session.start(capture)
            withTimeout(15_000) { session.state.first(isDone) }
            delay(50) // Let the collector record the final state.
        } finally {
            collector.cancel()
            session.stop()
        }
        return states
    }

    private fun entry(stage: DiagnosticStage, description: String, command: String? = null, response: String? = null) =
        DiagnosticTraceEntry(System.currentTimeMillis(), stage, description, command, response)
}
