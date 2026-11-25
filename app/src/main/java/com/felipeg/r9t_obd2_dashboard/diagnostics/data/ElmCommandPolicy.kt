package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError

/**
 * The only commands the app may send, checked before anything reaches the adapter. The bike only
 * ever receives Mode 01 requests ("show current data"), which read values and change nothing in
 * the ECU. Clearing trouble codes, test control, diagnostic sessions, security access, writes or
 * programming can never be sent, even by mistake. See docs/CONNECTION_OPTIONS.md.
 */
internal object ElmCommandPolicy {
    /** Adapter settings and queries: they configure the ELM327 itself and are not sent to the bike. */
    private val ADAPTER_COMMANDS = setOf("ATZ", "ATI", "ATE0", "ATH0", "ATRV", "ATSPA6", "ATSP0", "ATDP")

    /**
     * Mode 01 with one to six PIDs as uppercase hex pairs, optionally followed by a single-reply
     * count digit (1-F), such as `0100`, `010C0D` or `010C0D1`.
     */
    private val CURRENT_DATA_REQUEST = Regex("01(?:[0-9A-F]{2}){1,6}[1-9A-F]?")

    fun isAllowed(command: String): Boolean = command in ADAPTER_COMMANDS || CURRENT_DATA_REQUEST.matches(command)

    /** @throws ElmTransportException with [DiagnosticError.COMMAND_BLOCKED] for anything else. */
    fun requireAllowed(command: String) {
        if (!isAllowed(command)) throw ElmTransportException(DiagnosticError.COMMAND_BLOCKED)
    }
}
