package com.felipeg.r9t_obd2_dashboard.dashboard.needle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Readings arrive like the reference bike's ECU sends them: a new value every ~200 ms, each
 * repeated in between. Frames come every 16 ms (60 Hz).
 */
class TachometerNeedleTest {
    private val needle = TachometerNeedle()

    /** Feeds `values[i]` at `i * 200 ms` (repeated every 66 ms, as the app asks) and renders every frame. */
    private fun ride(values: List<Float>, extraMillis: Long = 600): List<Float> {
        val positions = mutableListOf<Float>()
        val end = values.size * 200L + extraMillis
        var nextReading = 0L
        for (now in 0L..end step 16) {
            while (nextReading <= now) {
                val index = (nextReading / 200).toInt().coerceAtMost(values.lastIndex)
                needle.onReading(nextReading, values[index])
                nextReading += 66
            }
            positions += needle.advance(now)!!
        }
        return positions
    }

    @Test
    fun startsAtTheFirstReading() {
        needle.onReading(0, 1.2f)
        assertEquals(1.2f, needle.advance(0)!!, 0f)
    }

    @Test
    fun hidesWithoutAReading() {
        assertNull(needle.advance(0))
        needle.onReading(0, 1.2f)
        needle.onReading(100, null)
        assertNull(needle.advance(116))
    }

    @Test
    fun neverStopsWhileTheEngineKeepsRising() {
        // A steady rise from 1,200 to 5,200 rpm, one new value every 200 ms.
        val rise = (0..20).map { 1.2f + it * 0.2f }
        val positions = ride(rise, extraMillis = 0)
        // From just after the first change arrives (264 ms, frame 17) to the last value's arrival.
        val climbing = positions.subList(17, positions.size - 15)
        climbing.zipWithNext().forEachIndexed { frame, (a, b) ->
            assertTrue("Needle stopped or went back at frame $frame: $a → $b", b > a)
        }
    }

    @Test
    fun reachesAndSettlesOnASteadyValueWithABarelyVisibleBounce() {
        val positions = ride(listOf(1.2f, 3.2f, 3.2f, 3.2f, 3.2f), extraMillis = 400)
        assertEquals("Settles on the reading", 3.2f, positions.last(), 0.01f)
        val overshoot = positions.max() - 3.2f
        assertTrue("Bounce of $overshoot (thousands) is more than 3 % of the 2,000 rpm step", overshoot <= 0.06f)
    }

    @Test
    fun followsABlipWithoutShootingPastIt() {
        // A quick blip: idle, up to 3,000 rpm in two values, straight back down.
        val positions = ride(listOf(1.2f, 2.0f, 3.0f, 2.2f, 1.2f, 1.2f, 1.2f), extraMillis = 400)
        val peak = positions.max()
        assertTrue("Reaches near the peak: $peak", peak >= 2.85f)
        assertTrue("No false rebound past the peak: $peak", peak <= 3.06f)
        assertTrue("Never dips well below idle: ${positions.min()}", positions.min() >= 1.14f)
        assertEquals(1.2f, positions.last(), 0.01f)
    }

    @Test
    fun repeatsOfTheSameValueAreNotNews() {
        val track = TargetTrack()
        track.reset(0, 1.2f)
        track.onValue(200, 2.0f)
        val before = track.at(300)
        track.onValue(266, 2.0f) // the ECU repeating itself
        assertEquals(before, track.at(300))
    }

    @Test
    fun learnsTheEcuUpdatePeriod() {
        val track = TargetTrack(initialPeriodMillis = 100f)
        track.reset(0, 1f)
        (1..30).forEach { track.onValue(it * 200L, 1f + it * 0.1f) }
        assertEquals(200f, track.periodMillis, 5f)
    }

    @Test
    fun aLateFrameDoesNotThrowTheNeedleOff() {
        needle.onReading(0, 1.2f)
        needle.advance(0)
        needle.onReading(200, 3.0f)
        val afterPause = needle.advance(5_000)!! // the app was in the background
        assertTrue("Within the scale after a long gap: $afterPause", afterPause in 1.2f..3.06f)
    }
}
