package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.GearPosition
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** What the engine is doing at one moment of the synthetic ride. */
internal data class EngineState(val rpm: Double, val speedKmh: Double, val gear: GearPosition)

/**
 * A 36-second ride that loops and keeps the needle busy: barely a second of idle, then blips in
 * neutral up to the shift light's red zone, a hard pull through four gears (the RPM drops at each
 * upshift), throttle on and off at speed, downshift blips, another pull and a stop with more
 * blips. Between keyframes the engine moves linearly, as fast as the R nineT's boxer revs.
 */
internal object SyntheticRide {
    const val LOOP_MILLIS = 36_000L

    private class Keyframe(val seconds: Double, val rpm: Double, val speedKmh: Double, val gear: GearPosition)

    private val N = GearPosition.Neutral
    private fun g(number: Int) = GearPosition.Forward(number)

    private val keyframes = listOf(
        Keyframe(0.0, 1_150.0, 0.0, N),
        Keyframe(0.8, 1_150.0, 0.0, N),
        Keyframe(1.1, 3_500.0, 0.0, N), // blips in neutral
        Keyframe(1.6, 1_400.0, 0.0, N),
        Keyframe(1.9, 5_200.0, 0.0, N),
        Keyframe(2.6, 1_600.0, 0.0, N),
        Keyframe(2.9, 7_900.0, 0.0, N), // into the shift light's red zone
        Keyframe(3.4, 7_900.0, 0.0, N),
        Keyframe(4.4, 1_300.0, 0.0, N),
        Keyframe(5.0, 1_150.0, 0.0, g(1)),
        Keyframe(5.3, 1_800.0, 3.0, g(1)), // pulling away hard
        Keyframe(7.0, 7_800.0, 40.0, g(1)),
        Keyframe(7.3, 4_800.0, 40.0, g(2)), // shift up: the RPM drops
        Keyframe(9.0, 7_600.0, 70.0, g(2)),
        Keyframe(9.3, 5_300.0, 70.0, g(3)),
        Keyframe(11.0, 7_400.0, 100.0, g(3)),
        Keyframe(11.3, 5_800.0, 100.0, g(4)),
        Keyframe(13.0, 7_000.0, 120.0, g(4)),
        Keyframe(13.4, 4_500.0, 105.0, g(4)), // throttle off and on
        Keyframe(14.0, 6_500.0, 112.0, g(4)),
        Keyframe(15.0, 3_500.0, 85.0, g(4)), // braking
        Keyframe(15.3, 4_600.0, 85.0, g(3)), // downshift blip
        Keyframe(16.2, 3_200.0, 60.0, g(3)),
        Keyframe(16.5, 4_400.0, 60.0, g(2)),
        Keyframe(17.2, 2_800.0, 40.0, g(2)),
        Keyframe(17.6, 6_800.0, 55.0, g(2)), // another pull
        Keyframe(18.8, 7_700.0, 75.0, g(2)),
        Keyframe(19.1, 5_400.0, 75.0, g(3)),
        Keyframe(20.5, 7_300.0, 100.0, g(3)),
        Keyframe(21.0, 4_000.0, 80.0, g(3)), // on and off the throttle through bends
        Keyframe(21.6, 6_600.0, 95.0, g(3)),
        Keyframe(22.2, 3_800.0, 75.0, g(3)),
        Keyframe(22.8, 6_200.0, 88.0, g(3)),
        Keyframe(24.0, 7_600.0, 110.0, g(3)),
        Keyframe(24.3, 5_900.0, 110.0, g(4)),
        Keyframe(25.5, 6_800.0, 125.0, g(4)),
        Keyframe(26.5, 3_600.0, 85.0, g(4)), // braking for a stop
        Keyframe(26.8, 4_700.0, 85.0, g(3)),
        Keyframe(27.8, 3_000.0, 55.0, g(3)),
        Keyframe(28.1, 4_300.0, 55.0, g(2)),
        Keyframe(29.0, 2_600.0, 35.0, g(2)),
        Keyframe(29.3, 4_000.0, 35.0, g(1)),
        Keyframe(30.5, 2_000.0, 15.0, g(1)),
        Keyframe(31.2, 1_150.0, 0.0, N),
        Keyframe(31.6, 1_150.0, 0.0, N),
        Keyframe(32.0, 4_200.0, 0.0, N), // blips at the stop
        Keyframe(32.5, 1_300.0, 0.0, N),
        Keyframe(32.8, 6_000.0, 0.0, N),
        Keyframe(33.5, 1_400.0, 0.0, N),
        Keyframe(33.8, 7_900.0, 0.0, N),
        Keyframe(34.2, 7_900.0, 0.0, N),
        Keyframe(35.2, 1_200.0, 0.0, N),
        Keyframe(36.0, 1_150.0, 0.0, N),
    )

    /** The engine at [elapsedMillis] into the ride, which repeats every [LOOP_MILLIS]. */
    fun at(elapsedMillis: Long): EngineState {
        val seconds = (elapsedMillis.mod(LOOP_MILLIS)) / 1_000.0
        val next = keyframes.indexOfFirst { it.seconds > seconds }.takeIf { it > 0 } ?: keyframes.lastIndex
        val from = keyframes[next - 1]
        val to = keyframes[next]
        val share = ((seconds - from.seconds) / (to.seconds - from.seconds)).coerceIn(0.0, 1.0)
        val rpm = from.rpm + (to.rpm - from.rpm) * share
        // A real idle hunts by a few RPM; at higher RPM it is lost in the movement.
        val hunting = if (rpm < 1_300) 9 * sin(seconds * 7.3) + 5 * sin(seconds * 17.1) else 0.0
        return EngineState(rpm + hunting, from.speedKmh + (to.speedKmh - from.speedKmh) * share, from.gear)
    }
}

/**
 * Answers the way the reference bike's ECU does: it renews its RPM and speed values only about
 * every [periodMillis] (± [jitterMillis], measured at 170–230 ms on 2026-11-25/26), and repeats
 * the last values to every request in between. RPM comes in quarter steps and speed in whole km/h,
 * as Mode 01 encodes them.
 */
internal class EcuSampler(
    private val periodMillis: Long = 200,
    private val jitterMillis: Long = 30,
    seed: Int = 7,
) {
    private val random = Random(seed)
    private var nextUpdateAt = Long.MIN_VALUE
    private var latched: EngineState? = null

    /** What the ECU replies at [nowMillis], given what the engine is doing then. */
    fun reply(nowMillis: Long, engine: () -> EngineState): EngineState {
        val current = latched
        if (current == null || nowMillis >= nextUpdateAt) {
            val state = engine()
            latched = state.copy(
                rpm = floor(state.rpm * 4) / 4,
                speedKmh = state.speedKmh.roundToInt().toDouble(),
            )
            // The ECU renews on its own clock, not when asked, so the next renewal counts from the
            // last one; requests in between only see the latched values.
            val from = if (current == null) nowMillis else nextUpdateAt
            nextUpdateAt = from + periodMillis + random.nextLong(-jitterMillis, jitterMillis + 1)
            if (nextUpdateAt <= nowMillis) nextUpdateAt = nowMillis + periodMillis
        }
        return latched!!
    }
}
