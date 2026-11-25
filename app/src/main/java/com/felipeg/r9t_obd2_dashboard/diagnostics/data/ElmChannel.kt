package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError

internal fun invalidResponse(): Nothing = throw ElmTransportException(DiagnosticError.INVALID_RESPONSE)

/**
 * One ELM327 link seen as requests and replies. Every command is checked against
 * [ElmCommandPolicy] and logged before it is sent; [pendingCommand] names the one still waiting
 * for its reply.
 */
internal class ElmChannel(private val transport: ElmTransport, private val recorder: SessionRecorder) {
    var pendingCommand: String? = null
        private set

    suspend fun open(adapterId: String) = transport.open(adapterId)

    /** Sends [command] and returns the raw reply. The caller records it, usually with a summary. */
    suspend fun transmit(command: String, description: String): String {
        ElmCommandPolicy.requireAllowed(command)
        pendingCommand = command
        recorder.sending(command, description)
        return transport.exchange(command).also { pendingCommand = null }
    }

    /** Sends [command] and records the reply as is. */
    suspend fun exchange(command: String, description: String): String {
        val response = transmit(command, description)
        recorder.record(description, command, response)
        return response
    }

    /** Sends a setting the adapter must acknowledge with OK. */
    suspend fun configure(command: String, description: String) {
        if (!ElmResponse(exchange(command, description)).isOk) invalidResponse()
    }

    fun takePartialResponse(): String? = transport.takePartialResponse()

    fun forgetPendingCommand() {
        pendingCommand = null
    }

    fun close() = transport.close()
}
