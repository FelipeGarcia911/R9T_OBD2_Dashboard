package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ElmReplyReaderTest {
    /** Hands out at most [chunk] bytes per read, like a Bluetooth stream delivering packets. */
    private class ChunkedStream(text: String, private val chunk: Int) : InputStream() {
        private val bytes = ByteArrayInputStream(text.toByteArray(Charsets.US_ASCII))
        var reads = 0
        override fun read(): Int = bytes.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            reads++
            return bytes.read(b, off, minOf(len, chunk))
        }
    }

    @Test
    fun readsAWholeReplyInBlocks() {
        val reply = "41 0C 1A F8 0D 20 \r\r>"
        val stream = ChunkedStream(reply, chunk = 64)

        assertEquals(reply, readReply(stream, StringBuffer()))
        assertEquals("One call for a reply that arrives at once", 1, stream.reads)
    }

    @Test
    fun joinsAReplySplitAcrossPackets() {
        val stream = ChunkedStream("SEARCHING...\r41 00 BF DE A8 13 \r\r>", chunk = 5)
        assertEquals("SEARCHING...\r41 00 BF DE A8 13 \r\r>", readReply(stream, StringBuffer()))
    }

    @Test
    fun stopsAtThePrompt() {
        assertEquals("OK\r>", readReply(ChunkedStream("OK\r>stray", chunk = 64), StringBuffer()))
    }

    @Test
    fun keepsWhatArrivedWhenTheStreamEnds() {
        val received = StringBuffer()
        assertThrows(IOException::class.java) { readReply(ChunkedStream("41 0C 1A", chunk = 4), received) }
        assertEquals("41 0C 1A", received.toString())
    }

    @Test
    fun rejectsAReplyThatNeverEnds() {
        val endless = object : InputStream() {
            override fun read() = 'A'.code
        }
        val failure = assertThrows(ElmTransportException::class.java) { readReply(endless, StringBuffer()) }
        assertEquals(DiagnosticError.INVALID_RESPONSE, failure.error)
    }
}
