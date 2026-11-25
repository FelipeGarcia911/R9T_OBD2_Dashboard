package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ObdPidsTest {
    @Test
    fun writesPidsAsTwoUppercaseHexDigits() {
        assertEquals("05", ObdPids.hex(0x05))
        assertEquals("A6", ObdPids.hex(0xA6))
    }

    @Test
    fun buildsMode01RequestsAndResponsePrefixes() {
        assertEquals("0100", ObdPids.request(0x00))
        assertEquals("01A0", ObdPids.request(0xA0))
        assertEquals("010C0D", ObdPids.RPM_AND_SPEED)
        assertEquals("410C", ObdPids.responsePrefix(ObdPids.ENGINE_RPM))
    }

    @Test
    fun addsAndRemovesTheSingleReplyCount() {
        assertEquals("010C0D1", ObdPids.singleResponse(ObdPids.RPM_AND_SPEED))
        assertEquals("010C0D", ObdPids.baseRequest("010C0D1"))
        assertEquals("0105", ObdPids.baseRequest("01051"))
        assertEquals("010C0D", ObdPids.baseRequest("010C0D"))
        assertEquals("ATRV", ObdPids.baseRequest("ATRV"))
    }
}
