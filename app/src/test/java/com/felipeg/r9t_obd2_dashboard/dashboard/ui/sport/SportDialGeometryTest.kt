package com.felipeg.r9t_obd2_dashboard.dashboard.ui.sport

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SportDialGeometryTest {
    @Test
    fun scaleRunsClockwiseFromLowerLeftOverTheTopToLowerRight() {
        assertEquals(171f, SportDialAngles.of(0f), 0f)
        assertEquals(270f, SportDialAngles.of(5f), 0f) // straight up
        assertEquals(369f, SportDialAngles.of(10f), 0f)
        assertEquals(SportDialAngles.SWEEP, SportDialAngles.sweep(0f, 10f), 0f)
    }

    @Test
    fun pointsLieOnTheDialInScreenCoordinates() {
        val center = Offset(100f, 100f)
        val top = SportDialAngles.point(center, 50f, 5f)
        assertEquals(100f, top.x, 1e-3f)
        assertEquals(50f, top.y, 1e-3f)
        val zero = SportDialAngles.point(center, 50f, 0f)
        assertTrue("zero sits left of center", zero.x < 60f)
        assertTrue("zero sits just below center", zero.y > 100f)
    }

    @Test
    fun dialFitsTheInstrumentAreaOnWidePhonesAndItsWidthOnSquareScreens() {
        // 800 x 360 dp phone minus the 56 dp top and bottom bars and the cyan rule.
        val phone = SportDialLayout.fit(800.dp, 246.dp)
        assertEquals(400.dp, phone.centerX)
        assertEquals((246.dp - 22.dp) / 1.18f, phone.radius)
        assertEquals(20.dp + phone.radius, phone.centerY)
        val square = SportDialLayout.fit(500.dp, 480.dp)
        assertEquals(180.dp, square.radius)
    }
}
