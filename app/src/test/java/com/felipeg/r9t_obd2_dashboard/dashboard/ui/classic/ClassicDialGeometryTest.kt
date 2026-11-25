package com.felipeg.r9t_obd2_dashboard.dashboard.ui.classic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicDialGeometryTest {
    @Test
    fun segmentsSplitEveryThousandAndAtTheLimit() {
        assertEquals(
            listOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 8.5f, 9f, 10f),
            ClassicScaleSegments.bounds(limitStart = 8.5f, max = 10f),
        )
    }

    @Test
    fun layoutFillsPhoneHeightAndCentersWithinItsMaximumAspect() {
        // 800 x 360 dp phone minus the TFT bars: wider than the instrument's maximum aspect.
        val phone = ClassicDialLayout.fit(800.dp, 240.dp)
        assertEquals(240.dp, phone.unit)
        assertEquals(240.dp * ClassicDialLayout.MAX_ASPECT, phone.width)
        assertEquals((800.dp - phone.width) / 2, phone.left)
        val narrow = ClassicDialLayout.fit(500.dp, 240.dp)
        assertEquals(0.dp, narrow.left)
        assertEquals(500.dp, narrow.width)
        assertEquals(narrow.aspect - ClassicDialLayout.GEAR_COLUMN, narrow.scaleEnd, 1e-4f)
    }

    @Test
    fun scaleClimbsFromItsFootUpTheLeftThenRunsAcrossTheTopToItsEnd() {
        val path = ClassicScalePath(scaleEnd = 2.4f)
        val zero = path.center(0f)
        val one = path.center(1f)
        val two = path.center(2f)
        val ten = path.center(10f)
        assertTrue("the foot climbs up and left", one.x < zero.x && one.y < zero.y)
        assertEquals("1 to 2 rises straight up", one.x, two.x, .005f)
        assertTrue(two.y < one.y)
        assertEquals(2.4f, ten.x, 1e-4f)
        assertEquals(path.center(5f).y, ten.y, 1e-4f)
        assertEquals(ten, path.center(12f))
    }

    @Test
    fun outsideIsLeftOnTheRisingPartAndAboveOnTheTopRun() {
        val path = ClassicScalePath(scaleEnd = 2.4f)
        assertTrue(path.edge(1.5f, .1f).x < path.center(1.5f).x)
        assertTrue(path.edge(6f, .1f).y < path.center(6f).y)
    }

    @Test
    fun cornersAreSharpAndParallelLinesMeetOnTheMiter() {
        val path = ClassicScalePath(scaleEnd = 2.4f)
        assertEquals(listOf(2.5f, 3f), path.corners(2f, 3.5f))
        assertTrue(path.corners(5f, 10f).isEmpty())
        // The offset corner lies on both offset runs, so the outline turns without a gap or step.
        val corner = path.edge(3f, .1f)
        assertCollinear(path.edge(2.6f, .1f), path.edge(2.8f, .1f), corner)
        assertCollinear(corner, path.edge(3.3f, .1f), path.edge(3.6f, .1f))
    }

    private fun assertCollinear(a: Offset, b: Offset, c: Offset) {
        val cross = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
        assertEquals(0f, cross, 1e-5f)
    }

    @Test
    fun cutsAreUprightUpToFiveThousandAndLeanMoreTowardTheEnd() {
        val path = ClassicScalePath(scaleEnd = 2.4f)
        assertEquals(0f, path.shear(2f), 0f)
        assertEquals(0f, path.shear(5f), 0f)
        assertTrue(path.shear(9f) > path.shear(7f))
        val outer = path.edge(8f, .1f, path.shear(8f))
        val inner = path.edge(8f, -.1f, path.shear(8f))
        assertTrue("outer end leans toward higher RPM", outer.x > inner.x)
    }

    @Test
    fun centerlineIsContinuous() {
        val path = ClassicScalePath(scaleEnd = 2.4f)
        val steps = (0..1000).map { path.center(it / 100f) }
        assertTrue(steps.zipWithNext().all { (a, b) -> (b - a).getDistance() < .01f })
    }
}
