package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.GearPosition
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** What the engine is doing at one moment of the synthetic ride. */
internal data class EngineState(val rpm: Double, val speedKmh: Double, val gear: GearPosition)

/**
 * A 36-second ride that loops, wider than the parked captures so the needle has range to show:
 * idle with its small hunting, throttle blips in neutral, a rev into the shift light's red zone,
 * then pulling away through four gears (the RPM drops at each shift), cruising, downshifting and
 * stopping. Between keyframes the engine moves linearly, as fast as the R nineT's boxer revs.
 */
internal object SyntheticRide {
    const val LOOP_MILLIS = 36_000L

    private class Keyframe(val seconds: Double, val rpm: Double, val speedKmh: Double, val gear: GearPosition)

    private val N = GearPosition.Neutral
    private fun g(number: Int) = GearPosition.Forward(number)

    private val keyframes = listOf(
        Keyframe(0.0, 1_150.0, 0.0, N),
        Keyframe(3.0, 1_150.0, 0.0, N),
        Keyframe(3.4, 3_000.0, 0.0, N), // quick blip
        Keyframe(4.2, 1_150.0, 0.0, N),
        Keyframe(6.0, 1_150.0, 0.0, N),
        Keyframe(6.5, 4_800.0, 0.0, N), // bigger blip
        Keyframe(7.6, 1_150.0, 0.0, N),
        Keyframe(9.0, 1_150.0, 0.0, N),
        Keyframe(9.8, 7_900.0, 0.0, N), // into the shift light's red zone
        Keyframe(10.3, 7_900.0, 0.0, N),
        Keyframe(11.6, 1_150.0, 0.0, N),
        Keyframe(13.0, 1_150.0, 0.0, g(1)),
        Keyframe(13.5, 1_600.0, 3.0, g(1)),
        Keyframe(16.0, 6_800.0, 38.0, g(1)),
        Keyframe(16.3, 4_300.0, 38.0, g(2)), // shift up: the RPM drops
        Keyframe(18.8, 7_200.0, 66.0, g(2)),
        Keyframe(19.1, 4_900.0, 66.0, g(3)),
        Keyframe(21.6, 7_000.0, 94.0, g(3)),
        Keyframe(22.0, 5_200.0, 94.0, g(4)),
        Keyframe(25.0, 5_600.0, 100.0, g(4)), // cruising
        Keyframe(28.0, 3_000.0, 70.0, g(4)),
        Keyframe(28.3, 4_000.0, 70.0, g(3)), // downshift blip
        Keyframe(31.0, 2_200.0, 40.0, g(3)),
        Keyframe(31.3, 3_000.0, 40.0, g(2)),
        Keyframe(33.5, 1_400.0, 10.0, g(2)),
        Keyframe(34.5, 1_150.0, 0.0, N),
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
