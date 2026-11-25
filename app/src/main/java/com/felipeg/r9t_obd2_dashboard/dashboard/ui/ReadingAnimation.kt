package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlin.math.roundToInt

/**
 * Settles in ~200 ms with no overshoot. Readings arrive every ~80 ms; a critically damped spring
 * keeps its velocity when the target changes, so gauges and digits move continuously instead of
 * stopping between samples. Shared by every display.
 */
internal val ReadingSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** The tachometer position (thousands of RPM) and the speed as a display draws them this frame. */
internal data class AnimatedReadings(val tachometer: Float?, val speedKmh: Int?)

/**
 * Moves the tachometer and speed toward [readings] with [ReadingSpring]. A missing reading stays
 * null, so displays show it as missing instead of animating toward zero.
 */
@Composable
internal fun animateReadings(readings: DashboardReadings): AnimatedReadings {
    val tachometer by animateFloatAsState(TachometerScale.fromRpm(readings.rpm), ReadingSpring, label = "rpm")
    val speed by animateFloatAsState((readings.speedKmh ?: 0.0).toFloat(), ReadingSpring, label = "speed")
    return AnimatedReadings(
        tachometer = readings.rpm?.let { tachometer },
        speedKmh = readings.speedKmh?.let { speed.roundToInt() },
    )
}
