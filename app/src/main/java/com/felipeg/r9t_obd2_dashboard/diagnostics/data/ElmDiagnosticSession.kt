package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticSession
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A live ELM327 session: runs the [ElmHandshake] and then the [ElmPoller] over one transport,
 * turns failures into [DiagnosticError]s and reconnects as [ReconnectPolicy] allows.
 */
internal class ElmDiagnosticSession(
    private val scope: CoroutineScope,
    transport: ElmTransport,
    reconnectDelayMillis: (attempt: Int) -> Long = ::reconnectBackoffMillis,
    private val startLog: suspend () -> ElmSessionLog? = { null },
) : DiagnosticSession {
    private val reconnectPolicy = ReconnectPolicy(reconnectDelayMillis)
    private val recorder = SessionRecorder()
    private val channel = ElmChannel(transport, recorder)
    private val handshake = ElmHandshake(channel, recorder, ::showProgress)
    private val poller = ElmPoller(channel, recorder, ::publishLive)
    private val mutableState = MutableStateFlow<DiagnosticState>(DiagnosticState.Disconnected)
    override val state = mutableState.asStateFlow()
    override val trace = recorder.trace
    private var job: Job? = null
    private var everLive = false
    private var reachedLive = false

    override suspend fun start(adapterId: String) {
        if (job?.isActive == true) return
        everLive = false
        recorder.clearTrace()
        recorder.stage = DiagnosticStage.BLUETOOTH
        channel.forgetPendingCommand()
        mutableState.value = DiagnosticState.ConnectingBluetooth
        job = scope.launch {
            var endReason = "Session ended"
            try {
                recorder.log = startLog()
                reconnectUntilFailed(adapterId)
            } catch (_: ElmLogException) {
                loggingFailed()
            } catch (cancelled: CancellationException) {
                endReason = "Session cancelled or disconnected"
                throw cancelled
            } finally {
                withContext(NonCancellable) { endSession(endReason) }
            }
        }
    }

    override suspend fun stop() {
        job?.cancelAndJoin()
        job = null
        recorder.stage = DiagnosticStage.BLUETOOTH
        recorder.record("Session disconnected", channel.pendingCommand)
        channel.forgetPendingCommand()
        mutableState.value = DiagnosticState.Disconnected
    }

    private suspend fun reconnectUntilFailed(adapterId: String) {
        var reconnects = 0
        while (true) {
            reachedLive = false
            val error = attempt(adapterId)
            if (reachedLive) reconnects = 0
            recordFailure(error)
            if (!reconnectPolicy.canRetry(error, everLive, reconnects)) {
                mutableState.value = DiagnosticState.Failed(recorder.stage, error)
                return
            }
            reconnects++
            val wait = reconnectPolicy.delayBefore(reconnects)
            mutableState.value = DiagnosticState.Reconnecting(reconnects, error)
            recorder.record(
                "Reconnecting in ${wait / 1_000.0} s · attempt $reconnects of ${reconnectPolicy.maxReconnects}",
            )
            delay(wait)
        }
    }

    /** Runs one connection until it fails. Log failures and cancellation propagate. */
    private suspend fun attempt(adapterId: String): DiagnosticError {
        recorder.stage = DiagnosticStage.BLUETOOTH
        channel.forgetPendingCommand()
        return try {
            poller.poll(handshake.connect(adapterId))
        } catch (error: ElmLogException) {
            throw error
        } catch (_: TimeoutCancellationException) {
            DiagnosticError.TIMEOUT
        } catch (error: ElmTransportException) {
            error.error
        } catch (_: SecurityException) {
            DiagnosticError.PERMISSION_DENIED
        } catch (_: IOException) {
            if (recorder.stage == DiagnosticStage.BLUETOOTH) DiagnosticError.ADAPTER_UNREACHABLE
            else DiagnosticError.CONNECTION_LOST
        } finally {
            channel.close()
        }
    }

    /** Keeps the reconnecting state visible until readings resume. */
    private fun showProgress(next: DiagnosticState) {
        if (mutableState.value !is DiagnosticState.Reconnecting) mutableState.value = next
    }

    private fun publishLive(live: DiagnosticState.Live) {
        mutableState.value = live
        reachedLive = true
        everLive = true
    }

    private suspend fun recordFailure(error: DiagnosticError) {
        try {
            val partial = channel.takePartialResponse()
            recorder.record(error.name.replace('_', ' ').lowercase(), channel.pendingCommand, partial, ElmLogEvent.ERROR)
        } finally {
            channel.forgetPendingCommand()
        }
    }

    /** Records what was left unanswered and closes the log. */
    private suspend fun endSession(reason: String) {
        try {
            val partial = channel.takePartialResponse()
            if (partial != null) {
                recorder.record("Incomplete response", channel.pendingCommand, partial, ElmLogEvent.ERROR)
            }
            recorder.record(reason, channel.pendingCommand, event = ElmLogEvent.SESSION_END)
            recorder.closeLog()
        } catch (_: ElmLogException) {
            loggingFailed()
        } finally {
            recorder.log = null
        }
    }

    private fun loggingFailed() {
        recorder.log = null
        mutableState.value = DiagnosticState.Failed(recorder.stage, DiagnosticError.LOG_WRITE_FAILED)
    }
}
