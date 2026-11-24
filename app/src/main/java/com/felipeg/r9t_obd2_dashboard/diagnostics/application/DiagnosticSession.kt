package com.felipeg.r9t_obd2_dashboard.diagnostics.application

import kotlinx.coroutines.flow.StateFlow

/** Application boundary shared by the live and simulated implementations. */
interface DiagnosticSession {
    val state: StateFlow<DiagnosticState>

    /** Starts a session for an opaque adapter ID. Connection progress is reported through state. */
    suspend fun start(adapterId: String)

    /** Stops the session and clears its readings before returning. */
    suspend fun stop()
}

sealed interface DiagnosticState {
    data object Disconnected : DiagnosticState
    data object ConnectingBluetooth : DiagnosticState
    data object WaitingForAdapter : DiagnosticState
    data object WaitingForEcu : DiagnosticState
    data class Live(
        val protocol: String?,
        val engineRpm: SignalReading,
        val speedKmh: SignalReading,
    ) : DiagnosticState

    /** The failure stage records the last step attempted. Previous readings are discarded. */
    data class Failed(val stage: DiagnosticStage, val error: DiagnosticError) : DiagnosticState
}

enum class DiagnosticStage { BLUETOOTH, ADAPTER, ECU, POLLING }

enum class DiagnosticError {
    PERMISSION_DENIED,
    BLUETOOTH_DISABLED,
    ADAPTER_UNREACHABLE,
    TIMEOUT,
    INVALID_RESPONSE,
    CONNECTION_LOST,
}

sealed interface SignalReading {
    data object Unavailable : SignalReading

    /** Timestamp measured against the same monotonic clock throughout the session. */
    data class Sample(val value: Double, val receivedAtMillis: Long) : SignalReading
}
