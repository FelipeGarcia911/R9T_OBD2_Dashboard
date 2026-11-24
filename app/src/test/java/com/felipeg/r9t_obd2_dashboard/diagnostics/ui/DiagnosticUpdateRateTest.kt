package com.felipeg.r9t_obd2_dashboard.diagnostics.ui

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiagnosticUpdateRateTest {
    @Test
    fun computesEachRateFromItsOwnSampleTimestamps() {
        val first = SignalReading.Sample(0.0, 1_000)
        assertEquals(2.0, updateRate(first, SignalReading.Sample(0.0, 1_500), null))
        assertEquals(1.0, updateRate(first, SignalReading.Sample(0.0, 2_000), null))
    }

    @Test
    fun retainsRateWhenAnotherSignalUpdatesWithoutANewSample() {
        val sample = SignalReading.Sample(30.0, 2_000)
        assertEquals(1.0, updateRate(sample, sample, 1.0))
    }

    @Test
    fun resetsRateWhenReadingsDisappearOrResume() {
        val sample = SignalReading.Sample(0.0, 1_000)
        assertNull(updateRate(sample, SignalReading.Unavailable, 1.0))
        assertNull(updateRate(SignalReading.Unavailable, sample, null))
        assertNull(updateRate(null, sample, null))
    }
}
