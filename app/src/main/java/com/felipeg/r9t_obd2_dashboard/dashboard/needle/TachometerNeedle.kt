package com.felipeg.r9t_obd2_dashboard.dashboard.needle

import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.Spring
import kotlin.math.max
import kotlin.math.min

/**
 * A tachometer needle that moves like a real one, from ECU readings that only change about five
 * times a second (measured on the reference bike: a new RPM value every ~200 ms, however often it
 * is asked). Two stages, as in a stepper-motor instrument cluster:
 *
 * 1. [TargetTrack] turns the stepped readings into a continuous target: repeats are ignored and
 *    each new value is joined to the previous one by a smooth curve over the ECU's update period.
 * 2. [NeedlePhysics] gives the needle mass and damping with Compose's physics spring, so it
 *    starts and stops smoothly and settles with a barely visible bounce.
 *
 * Android provides the spring; the interpolation between the ECU's sparse values is ours.
 *
 * Positions are on the tachometer scale (thousands of RPM); times are monotonic milliseconds.
 * Alerts such as the shift light must use the real readings, never this needle.
 */
internal class TachometerNeedle(
    private val track: TargetTrack = TargetTrack(),
    private val physics: NeedlePhysics = NeedlePhysics(),
) {
    private var visible = false
    private var lastFrameAt: Long? = null

    /** A reading arrived; null means there is none, which hides the needle until one arrives. */
    fun onReading(atMillis: Long, thousands: Float?) {
        if (thousands == null) {
            visible = false
            return
        }
        if (!visible) {
            track.reset(atMillis, thousands)
            physics.reset(thousands)
            visible = true
            lastFrameAt = atMillis
            return
        }
        track.onValue(atMillis, thousands)
    }

    /** Where to draw the needle at [nowMillis], or null to hide it. */
    fun advance(nowMillis: Long): Float? {
        if (!visible) return null
        val previous = lastFrameAt ?: nowMillis
        lastFrameAt = nowMillis
        val target = track.at(nowMillis)
        physics.step(target.position, (nowMillis - previous).coerceAtLeast(0) / 1_000f)
        return physics.position
    }
}

/** A target position and its speed, in scale units and scale units per second. */
internal data class Target(val position: Float, val velocity: Float)

/**
 * Joins the ECU's stepped readings into a continuous target. Each new value starts a cubic
 * (Hermite) curve from where the target is, at the speed it already has, to the new value over
 * the ECU's update period, so it arrives about when the next value is due. It arrives still
 * moving at [arrivalSpeed] of the latest change's speed: enough to flow into the next value
 * without stopping while the engine keeps changing, little enough to come to rest gently when
 * it settles. The target never goes past the newest real value.
 */
internal class TargetTrack(
    private val share: Float = 1f,
    private val arrivalSpeed: Float = 0.25f,
    initialPeriodMillis: Float = 200f,
) {
    /** The ECU's update period, learned from the time between new values. */
    var periodMillis = initialPeriodMillis
        private set

    private var lastValue = 0f
    private var lastValueAt = 0L
    private var segment = Segment(0L, 1f, 0f, 0f, 0f, 0f)

    fun reset(atMillis: Long, value: Float) {
        lastValue = value
        lastValueAt = atMillis
        segment = Segment(atMillis, 1f, value, 0f, value, 0f)
    }

    fun onValue(atMillis: Long, value: Float) {
        if (value == lastValue) return // A repeated reading is not news: the ECU has not updated.
        val gap = (atMillis - lastValueAt).toFloat()
        if (gap in MIN_PERIOD..MAX_GAP) {
            periodMillis = (periodMillis * (1 - LEARNING) + gap * LEARNING).coerceIn(MIN_PERIOD, MAX_PERIOD)
        }
        val trend = arrivalSpeed * (value - lastValue) / (max(gap, MIN_PERIOD) / 1_000f)
        val now = at(atMillis)
        segment = Segment(atMillis, share * periodMillis, now.position, now.velocity, value, trend)
        lastValue = value
        lastValueAt = atMillis
    }

    fun at(nowMillis: Long): Target = segment.at(nowMillis)

    private class Segment(
        val startMillis: Long,
        val durationMillis: Float,
        val from: Float,
        val fromVelocity: Float,
        val to: Float,
        val toVelocity: Float,
    ) {
        private val low = min(from, to)
        private val high = max(from, to)

        fun at(nowMillis: Long): Target {
            val s = (nowMillis - startMillis) / durationMillis
            if (s >= 1f) return Target(to, 0f)
            val seconds = durationMillis / 1_000f
            val s2 = s * s
            val s3 = s2 * s
            val position = (2 * s3 - 3 * s2 + 1) * from + (s3 - 2 * s2 + s) * seconds * fromVelocity +
                (-2 * s3 + 3 * s2) * to + (s3 - s2) * seconds * toVelocity
            val velocity = ((6 * s2 - 6 * s) * from + (3 * s2 - 4 * s + 1) * seconds * fromVelocity +
                (-6 * s2 + 6 * s) * to + (3 * s2 - 2 * s) * seconds * toVelocity) / seconds
            // Never beyond the newest real value, nor back past where the curve started.
            return Target(position.coerceIn(low, high), velocity)
        }
    }

    private companion object {
        const val MIN_PERIOD = 60f
        const val MAX_PERIOD = 400f
        const val MAX_GAP = 1_000f
        const val LEARNING = 0.2f
    }
}

/**
 * The needle's mass, spring and damping, using Compose's physics spring ([FloatSpringSpec]), which
 * solves the motion exactly for any frame time. Slightly underdamped ([dampingRatio] 0.8), like a
 * stepper-motor gauge: it starts and stops smoothly and settles with a barely visible bounce
 * (under 1 % on the reference bike's readings) while keeping within ~25 ms of the target.
 */
internal class NeedlePhysics(
    dampingRatio: Float = 0.8f,
    stiffness: Float = Spring.StiffnessMedium,
) {
    private val spring = FloatSpringSpec(dampingRatio, stiffness, visibilityThreshold = 0.0005f)

    var position = 0f
        private set
    var velocity = 0f
        private set

    fun reset(at: Float) {
        position = at
        velocity = 0f
    }

    fun step(target: Float, dtSeconds: Float) {
        // After a longer pause (the app in the background) the needle just catches up from there.
        val nanos = (dtSeconds.coerceAtMost(MAX_STEP_SECONDS) * 1_000_000_000L).toLong()
        val nextPosition = spring.getValueFromNanos(nanos, position, target, velocity)
        velocity = spring.getVelocityFromNanos(nanos, position, target, velocity)
        position = nextPosition
    }

    private companion object {
        const val MAX_STEP_SECONDS = 0.25f
    }
}
