package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.GearPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TachometerScaleTest {
    @Test
    fun convertsRpmToThousandsWithinTheScale() {
        assertEquals(1.8315f, TachometerScale.fromRpm(1_831.5), 1e-4f)
        assertEquals(0f, TachometerScale.fromRpm(null))
        assertEquals(TachometerScale.MAX, TachometerScale.fromRpm(14_000.0))
        assertEquals(0f, TachometerScale.fromRpm(-50.0))
    }

    @Test
    fun limitZoneStartsAtTheManualsMaximumEngineSpeed() {
        assertFalse(TachometerScale.isInLimitZone(8.49f))
        assertTrue(TachometerScale.isInLimitZone(8.5f))
    }

    @Test
    fun labelsNeutralAndForwardGears() {
        assertEquals("N", GearPosition.Neutral.dashboardLabel())
        assertEquals("4", GearPosition.Forward(4).dashboardLabel())
    }

    @Test
    fun selectorOffersSportFirstAndOpensOnIt() {
        assertEquals(listOf("Sport", "Classic"), DashboardLayout.entries.map { it.title })
        assertEquals(DashboardLayout.SPORT, DashboardLayout.DEFAULT)
    }
}
