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
}
