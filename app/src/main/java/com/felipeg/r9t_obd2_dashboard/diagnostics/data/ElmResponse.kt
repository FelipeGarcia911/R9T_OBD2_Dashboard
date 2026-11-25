package com.felipeg.r9t_obd2_dashboard.diagnostics.data

private const val NO_DATA = "NO DATA"

/** The adapter found no protocol to talk to the ECU, usually because the ignition is off. */
private const val UNABLE_TO_CONNECT = "UNABLE TO CONNECT"

/** Lines that echo a command or report an error instead of answering it. */
private val NON_ANSWERS = setOf("ATI", "ATDP", "?", "ERROR", NO_DATA)

/**
 * One ELM327 reply: the text before the `>` prompt, one line per ECU response or status
 * message. Every parser reads replies through here so they agree on what a line is.
 */
@JvmInline
internal value class ElmResponse(private val raw: String) {
    /** Trimmed lines, including empty ones and the adapter's status messages. */
    val lines: Sequence<String> get() = raw.substringBefore('>').lineSequence().map(String::trim)

    /** Lines without whitespace and uppercased, ready to match against hex frames such as `410C1AF8`. */
    val frames: Sequence<String> get() = lines.map { line -> line.filterNot(Char::isWhitespace).uppercase() }

    val isOk: Boolean get() = lines.any { it == "OK" }

    val isNoData: Boolean get() = hasStatus(NO_DATA)

    /** The ECU did not answer: NO DATA, or UNABLE TO CONNECT when no protocol was found. */
    val ecuDidNotAnswer: Boolean get() = hasStatus(NO_DATA, UNABLE_TO_CONNECT)

    /** The first line that answers the command, such as the adapter name or the protocol. */
    val answer: String? get() = lines.firstOrNull { it.isNotEmpty() && it !in NON_ANSWERS }

    private fun hasStatus(vararg statuses: String): Boolean =
        lines.any { line -> statuses.any { line.equals(it, ignoreCase = true) } }
}
