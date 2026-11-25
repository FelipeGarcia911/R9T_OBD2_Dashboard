package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObdPidDecoderTest {
    @Test
    fun decodesSupportedPidsInNetworkBitOrder() {
        assertEquals(setOf(1, 12, 13, 32), ObdPidDecoder.supportedPids("41 00 80 18 00 01"))
        assertEquals(emptySet<Int>(), ObdPidDecoder.supportedPids("410000000000"))
    }

    @Test
    fun decodesRpmAndSpeedIncludingZero() {
        assertEquals(1726.25, ObdPidDecoder.engineRpm("41 0c 1A F9")!!, 0.0)
        assertEquals(0.0, ObdPidDecoder.engineRpm("410C0000")!!, 0.0)
        assertEquals(88, ObdPidDecoder.speedKmh("41 0D 58"))
        assertEquals(0, ObdPidDecoder.speedKmh("410D00"))
    }

    @Test
    fun skipsEchoStatusLinesAndUnrelatedPids() {
        val response = "010D\rSEARCHING...\r41 0C 1A F9\r41 0D 58\r>"
        assertEquals(88, ObdPidDecoder.speedKmh(response))
        assertNull(ObdPidDecoder.speedKmh("41 0C 1A F9"))
    }

    @Test
    fun rejectsMissingTruncatedAndMalformedData() {
        assertNull(ObdPidDecoder.speedKmh("NO DATA\r>"))
        assertNull(ObdPidDecoder.engineRpm("41 0C 1A"))
        assertNull(ObdPidDecoder.speedKmh("41 0D ZZ"))
        assertNull(ObdPidDecoder.speedKmh("41 0D +1"))
        assertNull(ObdPidDecoder.speedKmh("41 0D 58 FF"))
        assertNull(ObdPidDecoder.supportedPids("41 00 80 18"))
    }

    @Test
    fun decodesCombinedResponsesInOneOrSeveralFrames() {
        val lengths = mapOf(0x0C to 2, 0x0D to 1)
        assertEquals(mapOf(0x0C to listOf(0x12, 0x0E), 0x0D to listOf(0x58)),
            ObdPidDecoder.multiplePids("41 0C 12 0E 0D 58 \r\r>", lengths))
        assertEquals(mapOf(0x0D to listOf(0), 0x0C to listOf(0x1C, 0x9E)),
            ObdPidDecoder.multiplePids("SEARCHING...\r41 0D 00\r41 0C 1C 9E\r>", lengths))
        assertEquals(mapOf(0x0C to listOf(0x1C, 0x9E)), ObdPidDecoder.multiplePids("41 0C 1C 9E\r>", lengths))
    }

    @Test
    fun decodesTheCombinedRpmAndSpeedReply() {
        assertEquals(RpmAndSpeed(1726.0, 32.0), ObdPidDecoder.rpmAndSpeed("41 0C 1A F8 0D 20\r>"))
        assertEquals(RpmAndSpeed(1726.0, null), ObdPidDecoder.rpmAndSpeed("41 0C 1A F8\r>"))
        assertEquals(RpmAndSpeed(null, null), ObdPidDecoder.rpmAndSpeed("NO DATA\r>"))
    }

    @Test
    fun rejectsCombinedResponsesThatCannotBeParsed() {
        val lengths = mapOf(0x0C to 2, 0x0D to 1)
        assertEquals(emptyMap<Int, List<Int>>(), ObdPidDecoder.multiplePids("NO DATA\r>", lengths))
        assertEquals(mapOf(0x0C to listOf(0x1C, 0x9E)),
            ObdPidDecoder.multiplePids("41 0C 1C 9E 0D\r>", lengths))
        assertEquals(emptyMap<Int, List<Int>>(), ObdPidDecoder.multiplePids("41 0C 1C ZZ 0D 00\r>", lengths))
    }
}
