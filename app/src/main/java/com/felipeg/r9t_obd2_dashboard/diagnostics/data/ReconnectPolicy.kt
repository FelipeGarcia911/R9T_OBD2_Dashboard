package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError

internal const val MAX_RECONNECTS = 10

/** Errors that another attempt cannot fix without the rider's intervention. */
private val PERMANENT_ERRORS = setOf(
    DiagnosticError.PERMISSION_DENIED,
    DiagnosticError.BLUETOOTH_DISABLED,
    DiagnosticError.LOG_WRITE_FAILED,
)

/** 1 s, 2 s, 4 s, 8 s, then every 10 s. */
internal fun reconnectBackoffMillis(attempt: Int): Long =
    (1_000L shl (attempt - 1).coerceIn(0, 4)).coerceAtMost(10_000L)

/**
 * Decides whether a session that lost its link reconnects on its own. Only a session that already
 * produced readings retries: a first connection that fails is reported so the rider can fix the
 * setup.
 */
internal class ReconnectPolicy(
    private val backoffMillis: (attempt: Int) -> Long = ::reconnectBackoffMillis,
    val maxReconnects: Int = MAX_RECONNECTS,
) {
    fun canRetry(error: DiagnosticError, everLive: Boolean, reconnectsSoFar: Int): Boolean =
        everLive && error !in PERMANENT_ERRORS && reconnectsSoFar < maxReconnects

    /** The wait before reconnect [attempt], starting at 1. */
    fun delayBefore(attempt: Int): Long = backoffMillis(attempt)
}
