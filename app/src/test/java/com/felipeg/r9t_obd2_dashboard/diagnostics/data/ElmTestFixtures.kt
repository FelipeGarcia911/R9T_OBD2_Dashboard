package com.felipeg.r9t_obd2_dashboard.diagnostics.data

/**
 * Replies an ELM327 v1.5 gives to the handshake commands. Like many clones it rejects Mode 01
 * requests that carry a single-reply count, so sessions fall back to plain requests. Anything
 * else fails the test.
 */
internal fun initReply(command: String): String = when {
    command == "ATZ" || command == "ATI" -> "ELM327 v1.5\r>"
    command in setOf("ATE0", "ATH0", "ATS0", "ATSPA6", "ATSP0") -> "OK\r>"
    command == "ATRV" -> "12.6V\r>"
    command.startsWith("01") && command.length % 2 == 1 -> "?\r>"
    else -> error("Unexpected command: $command")
}

/** Answers each command with [reply] and records what the session sent. */
internal class ScriptedTransport(
    private var partialResponse: String? = null,
    private val onOpen: (attempt: Int) -> Unit = {},
    private val reply: suspend (String) -> String,
) : ElmTransport {
    override fun takePartialResponse(): String? = partialResponse.also { partialResponse = null }
    val commands = mutableListOf<String>()
    var closed = false
    var opens = 0
    override suspend fun open(adapterId: String) {
        closed = false
        onOpen(++opens)
    }
    override suspend fun exchange(command: String): String {
        commands += command
        return reply(command)
    }
    override fun close() {
        closed = true
    }
}
