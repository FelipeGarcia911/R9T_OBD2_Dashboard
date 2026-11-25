package com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TftGaugeRangeTest {
    private val range = TftGaugeRange(min = 40.0, max = 140.0, warnFrom = 130.0)

    @Test
    fun lightsBarsInProportionAndClampsToTheGauge() {
        assertEquals(0, range.litBars(null))
        assertEquals(0, range.litBars(20.0))
        assertEquals(7, range.litBars(113.0))
        assertEquals(10, range.litBars(150.0))
    }

    @Test
    fun onlyTheTopBarSitsInTheWarningZone() {
        assertFalse(range.isWarning(8))
        assertTrue(range.isWarning(9))
    }

    @Test
    fun longBarsStepInwardFromTheTopRowToTheBottomRow() {
        assertEquals(TftStepProfile.SHORTEST, TftStepProfile.longBarShare(0, 12), 1e-6f)
        assertEquals(1f, TftStepProfile.longBarShare(11, 12), 1e-6f)
        val shares = (0 until 12).map { TftStepProfile.longBarShare(it, 12) }
        assertTrue(shares.zipWithNext().all { (lower, upper) -> upper > lower })
    }
}
