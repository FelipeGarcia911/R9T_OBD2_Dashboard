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

    @Test
    fun scaleSplitsEveryThousandThenStripesTheLimitZone() {
        val segments = SportScaleSegments.build(limitStart = 8.5f, max = 10f)
        val white = segments.filterNot { it.limitZone }
        val red = segments.filter { it.limitZone }
        assertEquals(9, white.size) // 0-1 ... 7-8, then 8-8.5
        assertEquals(6, red.size) // 8.5-10 in 250 rpm stripes
        val gap = SportScaleSegments.GAP / 2
        assertEquals(gap, white.first().from, 1e-5f)
        assertEquals(1f - gap, white.first().to, 1e-5f)
        assertEquals(8.5f - gap, white.last().to, 1e-5f)
        assertEquals(8.5f + gap, red.first().from, 1e-5f)
        assertEquals(10f - gap, red.last().to, 1e-5f)
        assertTrue(segments.zipWithNext().all { (a, b) -> a.to < b.from })
    }
}
