package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlin.math.roundToInt

/**
 * Settles in ~100 ms with no overshoot. Readings arrive every ~65 ms; a critically damped spring
 * keeps its velocity when the target changes, so gauges and digits move continuously instead of
 * stopping between samples, and trails a rising reading by ~50 ms. Shared by every display.
 */
internal val ReadingSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)

/**
 * The tachometer position (thousands of RPM) and the speed as a display draws them this frame.
 * They are read on demand: reading them while drawing redraws only what uses them each frame,
 * instead of recomposing the whole display. A missing reading reads as null.
 */
@Stable
internal class AnimatedReadings(val tachometer: () -> Float?, val speedKmh: () -> Int?)

/**
 * Moves the tachometer and speed toward [readings] with [ReadingSpring]. A missing reading stays
 * null, so displays show it as missing instead of animating toward zero.
 */
@Composable
internal fun animateReadings(readings: DashboardReadings): AnimatedReadings {
    val tachometer = animateFloatAsState(TachometerScale.fromRpm(readings.rpm), ReadingSpring, label = "rpm")
    val speed = animateFloatAsState((readings.speedKmh ?: 0.0).toFloat(), ReadingSpring, label = "speed")
    val hasRpm = readings.rpm != null
    val hasSpeed = readings.speedKmh != null
    return remember(hasRpm, hasSpeed, tachometer, speed) {
        AnimatedReadings(
            tachometer = { if (hasRpm) tachometer.value else null },
            speedKmh = { if (hasSpeed) speed.value.roundToInt() else null },
        )
    }
}
