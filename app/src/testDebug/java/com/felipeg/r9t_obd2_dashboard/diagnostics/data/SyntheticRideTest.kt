package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.GearPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The simulation must behave like the reference bike as recorded in field-logs/2026-11-25 and
 * 2026-11-26: polled every ~66 ms, the ECU renews RPM about every 200 ms (170–230 ms), so
 * 54–74 % of replies repeat the previous value; RPM comes in quarter steps.
 */
class SyntheticRideTest {
    /** Polls like the live session for [seconds] and returns (time, reply) pairs. */
    private fun poll(seconds: Int): List<Pair<Long, EngineState>> {
        val ecu = EcuSampler()
        return (0L until seconds * 1_000L step 66).map { now -> now to ecu.reply(now) { SyntheticRide.at(now) } }
    }

    @Test
    fun repeatsValuesLikeTheBikesEcu() {
        val replies = poll(SyntheticRide.LOOP_MILLIS.toInt() / 1_000)
        val repeats = replies.zipWithNext().count { (a, b) -> a.second.rpm == b.second.rpm }
        val share = 100.0 * repeats / replies.size
        assertTrue("Repeated replies: $share % (bike: 54–74 %)", share in 54.0..74.0)
    }

    @Test
    fun renewsValuesAboutEvery200Milliseconds() {
        val replies = poll(30)
        val changes = replies.zipWithNext().filter { (a, b) -> a.second != b.second }.map { it.second.first }
        val gaps = changes.zipWithNext { a, b -> b - a }.sorted()
        val median = gaps[gaps.size / 2]
        assertTrue("Median time between new values: $median ms (bike: ~200 ms)", median in 180L..240L)
    }

    @Test
    fun encodesLikeMode01() {
        poll(36).forEach { (_, reply) ->
            assertEquals("RPM in quarter steps", 0.0, reply.rpm * 4 % 1, 0.0)
            assertEquals("Speed in whole km/h", 0.0, reply.speedKmh % 1, 0.0)
        }
    }

    @Test
    fun coversIdleBlipsTheShiftLightAndGearChanges() {
        val states = (0L until SyntheticRide.LOOP_MILLIS step 50).map(SyntheticRide::at)
        assertTrue("Idles near 1,150 rpm", states.any { it.rpm in 1_100.0..1_200.0 })
        assertTrue("Reaches the shift light's red zone (7,750 rpm)", states.any { it.rpm >= 7_750 })
        assertTrue("Rides through four gears", states.any { it.gear == GearPosition.Forward(4) })
        // An upshift: the gear goes up and the RPM falls.
        val shift = states.zipWithNext().any { (a, b) ->
            (a.gear as? GearPosition.Forward)?.number?.let { from ->
                (b.gear as? GearPosition.Forward)?.number == from + 1
            } == true && b.rpm < a.rpm
        }
        assertTrue("RPM drops at an upshift", shift)
    }

    @Test
    fun loopsSeamlessly() {
        assertEquals(SyntheticRide.at(0).rpm, SyntheticRide.at(SyntheticRide.LOOP_MILLIS).rpm, 0.001)
        assertEquals(SyntheticRide.at(1_000).rpm, SyntheticRide.at(SyntheticRide.LOOP_MILLIS + 1_000).rpm, 0.001)
    }
}
