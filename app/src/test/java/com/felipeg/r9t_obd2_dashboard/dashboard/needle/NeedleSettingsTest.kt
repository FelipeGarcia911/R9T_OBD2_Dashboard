package com.felipeg.r9t_obd2_dashboard.dashboard.needle

import androidx.compose.animation.core.Spring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NeedleSettingsTest {
    @Test
    fun defaultsToTheFeelConfirmedOnTheBike() {
        val defaults = NeedleSettings()
        assertEquals(Spring.DampingRatioNoBouncy, defaults.dampingRatio, 0f)
        assertEquals(Spring.StiffnessMediumLow, defaults.stiffness, 0f)
        assertTrue(defaults.isDefault)
        assertFalse(defaults.withStiffness(800f).isDefault)
    }

    @Test
    fun snapsDampingToSteps() {
        assertEquals(0.75f, NeedleSettings().withDamping(0.76f).dampingRatio, 0.0001f)
        assertEquals(NeedleSettings.MIN_DAMPING, NeedleSettings().withDamping(0f).dampingRatio, 0f)
        assertEquals(NeedleSettings.MAX_DAMPING, NeedleSettings().withDamping(2f).dampingRatio, 0f)
    }

    @Test
    fun stiffnessSliderIsLogarithmicAndRoundTrips() {
        assertEquals(NeedleSettings.MIN_STIFFNESS, NeedleSettings().withStiffnessPosition(0f).stiffness, 0f)
        assertEquals(387f, NeedleSettings().withStiffnessPosition(0.5f).stiffness, 1f) // geometric middle of 50–3,000
        assertEquals(NeedleSettings.MAX_STIFFNESS, NeedleSettings().withStiffnessPosition(1f).stiffness, 0f)
        assertEquals(400f, NeedleSettings().withStiffnessPosition(NeedleSettings().stiffnessPosition).stiffness, 1f)
    }

    @Test
    fun describesTheBounceAndSettlingTime() {
        assertEquals(0f, NeedleSettings().overshootPercent, 0f)
        assertEquals(2.8f, NeedleSettings(dampingRatio = 0.75f).overshootPercent, 0.1f)
        assertEquals(292, NeedleSettings().settleMillis) // critically damped, StiffnessMediumLow
        assertTrue(NeedleSettings(stiffness = 1_500f).settleMillis < NeedleSettings().settleMillis)
    }

    @Test
    fun rejectsValuesOutsideTheRanges() {
        assertThrows(IllegalArgumentException::class.java) { NeedleSettings(dampingRatio = 0.1f) }
        assertThrows(IllegalArgumentException::class.java) { NeedleSettings(stiffness = 10_000f) }
    }
}
