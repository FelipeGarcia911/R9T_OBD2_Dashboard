package com.felipeg.r9t_obd2_dashboard.dashboard.shiftlight

import com.felipeg.r9t_obd2_dashboard.dashboard.shiftlight.ShiftLightPhase.APPROACHING
import com.felipeg.r9t_obd2_dashboard.dashboard.shiftlight.ShiftLightPhase.OFF
import com.felipeg.r9t_obd2_dashboard.dashboard.shiftlight.ShiftLightPhase.SHIFT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftLightTest {
    private val settings = ShiftLightSettings(startRpm = 7_000, shiftRpm = 7_750, frequencyHz = 4)

    @Test
    fun phasesFollowTheStartAndShiftRpm() {
        assertEquals(OFF, ShiftLightRules.next(OFF, 6_999.0, settings))
        assertEquals(APPROACHING, ShiftLightRules.next(OFF, 7_000.0, settings))
        assertEquals(SHIFT, ShiftLightRules.next(OFF, 7_750.0, settings))
        assertEquals(SHIFT, ShiftLightRules.next(OFF, 9_000.0, settings))
    }

    @Test
    fun staysDarkWhenDisabledOrWithoutAReading() {
        assertEquals(OFF, ShiftLightRules.next(SHIFT, 8_000.0, settings.copy(enabled = false)))
        assertEquals(OFF, ShiftLightRules.next(SHIFT, null, settings))
    }

    @Test
    fun hysteresisKeepsAPhaseUntilTheEngineSpeedClearlyDrops() {
        val margin = ShiftLightRules.HYSTERESIS_RPM
        assertEquals(SHIFT, ShiftLightRules.next(SHIFT, 7_750.0 - margin, settings))
        assertEquals(APPROACHING, ShiftLightRules.next(SHIFT, 7_749.0 - margin, settings))
        assertEquals(APPROACHING, ShiftLightRules.next(APPROACHING, 7_000.0 - margin, settings))
        assertEquals(OFF, ShiftLightRules.next(APPROACHING, 6_999.0 - margin, settings))
        // Rising still needs the full threshold.
        assertEquals(APPROACHING, ShiftLightRules.next(APPROACHING, 7_700.0, settings))
    }

    @Test
    fun approachingIsSteadyAndTheShiftPointFlashesAtTheFrequency() {
        assertTrue(ShiftLightRules.isLit(APPROACHING, 4, 130))
        assertFalse(ShiftLightRules.isLit(OFF, 4, 0))
        // 4 Hz: on for 125 ms, off for 125 ms, starting lit.
        assertTrue(ShiftLightRules.isLit(SHIFT, 4, 0))
        assertTrue(ShiftLightRules.isLit(SHIFT, 4, 124))
        assertFalse(ShiftLightRules.isLit(SHIFT, 4, 125))
        assertTrue(ShiftLightRules.isLit(SHIFT, 4, 250))
        assertTrue("0 Hz is a steady light", ShiftLightRules.isLit(SHIFT, 0, 125))
    }

    @Test
    fun settingsSnapToStepsAndNeverCross() {
        val moved = settings.withStart(7_620)
        assertEquals(7_500, moved.startRpm)
        val pushed = settings.withStart(8_000)
        assertEquals(8_000, pushed.startRpm)
        assertEquals(8_250, pushed.shiftRpm)
        val pulled = settings.withShift(6_000)
        assertEquals(6_000, pulled.shiftRpm)
        assertEquals(5_750, pulled.startRpm)
        assertEquals(8_250, settings.withStart(9_000).startRpm)
        assertEquals(10, settings.withFrequency(25).frequencyHz)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAShiftRpmBelowTheStart() {
        ShiftLightSettings(startRpm = 7_000, shiftRpm = 6_500)
    }
}
