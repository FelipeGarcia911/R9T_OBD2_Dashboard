package com.felipeg.r9t_obd2_dashboard.diagnostics.ui

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticSession
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticTraceEntry
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticViewModelTest {
    private val session = FakeSession()

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun connectAndDisconnectReachTheSession() {
        val model = DiagnosticViewModel { session }

        model.connect("00:11:22:33:44:55")
        model.disconnect()

        assertEquals(listOf("start:00:11:22:33:44:55", "stop"), session.calls)
        assertSame(session.trace, model.trace)
    }

    /** Current behavior: a tap while the previous action is still running is dropped, not queued. */
    @Test
    fun dropsAnActionWhileThePreviousOneIsStillRunning() {
        val startFinished = CompletableDeferred<Unit>()
        session.startGate = startFinished
        val model = DiagnosticViewModel { session }

        model.connect("adapter")
        model.disconnect()
        assertEquals(listOf("start:adapter"), session.calls)

        startFinished.complete(Unit)
        model.disconnect()
        assertEquals(listOf("start:adapter", "stop"), session.calls)
    }

    @Test
    fun derivesUpdateRatesFromConsecutiveSamplesAndClearsThemWhenNotLive() {
        val model = DiagnosticViewModel { session }

        session.state.value = live(rpmAt = 1_000, speedAt = 1_000)
        assertNull(model.uiState.value.rpmHz)
        assertNull(model.uiState.value.speedHz)

        session.state.value = live(rpmAt = 1_100, speedAt = 1_250)
        assertEquals(10.0, model.uiState.value.rpmHz!!, 1e-9)
        assertEquals(4.0, model.uiState.value.speedHz!!, 1e-9)

        // An unchanged RPM timestamp keeps the last rate while speed updates.
        session.state.value = live(rpmAt = 1_100, speedAt = 1_750)
        assertEquals(10.0, model.uiState.value.rpmHz!!, 1e-9)
        assertEquals(2.0, model.uiState.value.speedHz!!, 1e-9)

        val reconnecting = DiagnosticState.Reconnecting(1, DiagnosticError.CONNECTION_LOST)
        session.state.value = reconnecting
        assertEquals(DiagnosticUiState(session = reconnecting), model.uiState.value)

        // Rates start over once readings resume.
        session.state.value = live(rpmAt = 5_000, speedAt = 5_000)
        assertNull(model.uiState.value.rpmHz)
    }

    private fun live(rpmAt: Long, speedAt: Long) = DiagnosticState.Live(
        protocol = "CAN",
        engineRpm = SignalReading.Sample(1_200.0, rpmAt),
        speedKmh = SignalReading.Sample(40.0, speedAt),
    )

    private class FakeSession : DiagnosticSession {
        override val state = MutableStateFlow<DiagnosticState>(DiagnosticState.Disconnected)
        override val trace = MutableStateFlow<List<DiagnosticTraceEntry>>(emptyList())
        val calls = mutableListOf<String>()
        var startGate: CompletableDeferred<Unit>? = null

        override suspend fun start(adapterId: String) {
            calls += "start:$adapterId"
            startGate?.await()
        }

        override suspend fun stop() {
            calls += "stop"
        }
    }
}
