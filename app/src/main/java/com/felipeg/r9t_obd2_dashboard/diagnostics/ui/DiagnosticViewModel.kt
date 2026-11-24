package com.felipeg.r9t_obd2_dashboard.diagnostics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticSession
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class DiagnosticUiState(
    val session: DiagnosticState = DiagnosticState.Disconnected,
    val rpmHz: Double? = null,
    val speedHz: Double? = null,
)

internal class DiagnosticViewModel(
    createSession: (kotlinx.coroutines.CoroutineScope) -> DiagnosticSession,
) : ViewModel() {
    private val session = createSession(viewModelScope)
    private val mutableState = MutableStateFlow(DiagnosticUiState())
    val uiState = mutableState.asStateFlow()
    val trace = session.trace
    private var action: Job? = null

    init {
        viewModelScope.launch {
            session.state.collect { current ->
                val previous = mutableState.value
                val live = current as? DiagnosticState.Live
                val previousLive = previous.session as? DiagnosticState.Live
                mutableState.value = DiagnosticUiState(
                    session = current,
                    rpmHz = updateRate(previousLive?.engineRpm, live?.engineRpm, previous.rpmHz),
                    speedHz = updateRate(previousLive?.speedKmh, live?.speedKmh, previous.speedHz),
                )
            }
        }
    }

    fun connect(adapterId: String) = runAction { session.start(adapterId) }

    fun disconnect() = runAction { session.stop() }

    private fun runAction(block: suspend () -> Unit) {
        if (action?.isActive != true) action = viewModelScope.launch { block() }
    }

    companion object {
        fun factory(createSession: (kotlinx.coroutines.CoroutineScope) -> DiagnosticSession) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == DiagnosticViewModel::class.java)
                    return DiagnosticViewModel(createSession) as T
                }
            }
    }
}

internal fun updateRate(
    previous: SignalReading?,
    current: SignalReading?,
    lastRate: Double?,
): Double? {
    if (previous !is SignalReading.Sample || current !is SignalReading.Sample) return null
    val elapsed = current.receivedAtMillis - previous.receivedAtMillis
    return when {
        elapsed > 0 -> 1_000.0 / elapsed
        elapsed == 0L -> lastRate
        else -> null
    }
}
