package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError
import java.io.IOException
import java.io.InputStream

/** Longest reply the adapter may send before its prompt; anything longer is not an ELM327 reply. */
internal const val MAX_REPLY_LENGTH = 8_192

/**
 * Reads one ELM327 reply, up to and including its `>` prompt, in blocks rather than byte by byte:
 * each read from a Bluetooth stream is a call into the platform, so a reply arrives in one or two
 * calls instead of one per character. What has arrived so far is kept in [received], so a reply
 * cut short by a timeout can still be logged. Bytes after the prompt are not expected (the adapter
 * waits for the next command) and are dropped.
 */
internal fun readReply(input: InputStream, received: StringBuffer, buffer: ByteArray = ByteArray(256)): String {
    while (received.length < MAX_REPLY_LENGTH) {
        val count = input.read(buffer)
        if (count < 0) throw IOException("Adapter closed the stream")
        for (index in 0 until count) {
            val char = (buffer[index].toInt() and 0xFF).toChar()
            received.append(char)
            if (char == '>') return received.toString()
        }
    }
    throw ElmTransportException(DiagnosticError.INVALID_RESPONSE)
}
