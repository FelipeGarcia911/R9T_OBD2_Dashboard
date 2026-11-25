package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmResponseTest {
    @Test
    fun readsOnlyTheTextBeforeThePromptOneTrimmedLineAtATime() {
        val response = ElmResponse(" SEARCHING... \r\n41 0C 1A F8\r>ignored")
        // The carriage return before the prompt leaves an empty last line; parsers skip it.
        assertEquals(listOf("SEARCHING...", "41 0C 1A F8", ""), response.lines.toList())
        assertEquals(listOf("SEARCHING...", "410C1AF8", ""), response.frames.toList())
    }

    @Test
    fun framesAreUppercase() {
        assertEquals(listOf("410C1AF9"), ElmResponse("41 0c 1a f9>").frames.toList())
    }

    @Test
    fun recognizesOkOnItsOwnLine() {
        assertTrue(ElmResponse("ATE0\rOK\r>").isOk)
        assertTrue(ElmResponse(" OK \r>").isOk)
        assertFalse(ElmResponse("?\r>").isOk)
        assertFalse(ElmResponse("NOT OK\r>").isOk)
    }

    @Test
    fun tellsNoDataApartFromAnUnreachableEcu() {
        assertTrue(ElmResponse("SEARCHING...\rno data\r>").isNoData)
        assertTrue(ElmResponse("NO DATA\r>").ecuDidNotAnswer)
        assertTrue(ElmResponse("SEARCHING...\rUNABLE TO CONNECT\r>").ecuDidNotAnswer)
        assertFalse(ElmResponse("UNABLE TO CONNECT\r>").isNoData)
        assertFalse(ElmResponse("41 00 BE 1F A8 13\r>").ecuDidNotAnswer)
    }

    @Test
    fun answerSkipsEchoesBlankLinesAndErrors() {
        assertEquals("ELM327 v1.5", ElmResponse("ATI\r\rELM327 v1.5\r>").answer)
        assertEquals("ISO 15765-4 (CAN 11/500)", ElmResponse("ATDP\rISO 15765-4 (CAN 11/500)\r>").answer)
        assertNull(ElmResponse("?\r>").answer)
        assertNull(ElmResponse("ERROR\r>").answer)
        assertNull(ElmResponse("NO DATA\r>").answer)
        assertNull(ElmResponse(">").answer)
    }
}
