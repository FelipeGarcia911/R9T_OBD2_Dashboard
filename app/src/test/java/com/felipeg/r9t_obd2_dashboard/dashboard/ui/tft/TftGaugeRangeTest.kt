package com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun everyFourthRowFromTheTopAndTheBottomRowAreMajorWithRoundLabels() {
        val temperature = TftGaugeRange(min = 20.0, max = 140.0, warnFrom = 130.0, bars = 12, majorEvery = 4)
        assertEquals(listOf(0, 3, 7, 11), (0 until 12).filter(temperature::isMajor))
        assertEquals("140", temperature.scaleLabel(11, "°C"))
        assertEquals("100", temperature.scaleLabel(7, "°C"))
        assertEquals("60", temperature.scaleLabel(3, "°C"))
        assertEquals("°C", temperature.scaleLabel(0, "°C"))
        assertNull(temperature.scaleLabel(5, "°C"))
        assertTrue(temperature.isWarning(11))
        assertFalse(temperature.isWarning(10))
    }

    @Test
    fun rowsStackFromTheBottomWithGapsBetweenThem() {
        assertEquals(10f, TftStepProfile.rowHeight(bars = 4, height = 46f, gap = 2f), 1e-6f)
        assertEquals(36f, TftStepProfile.rowTop(bar = 0, bars = 4, height = 46f, gap = 2f), 1e-6f)
        assertEquals(0f, TftStepProfile.rowTop(bar = 3, bars = 4, height = 46f, gap = 2f), 1e-6f)
    }

    @Test
    fun longBarsStepInwardFromTheTopRowToTheBottomRow() {
        assertEquals(TftStepProfile.SHORTEST, TftStepProfile.longBarShare(0, 12), 1e-6f)
        assertEquals(1f, TftStepProfile.longBarShare(11, 12), 1e-6f)
        val shares = (0 until 12).map { TftStepProfile.longBarShare(it, 12) }
        assertTrue(shares.zipWithNext().all { (lower, upper) -> upper > lower })
    }
}
