package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.needle.LocalNeedleSettings
import kotlin.math.roundToInt

/**
 * Settles in ~200 ms with no overshoot. A critically damped spring keeps its velocity when the
 * target changes, so gauges and digits move continuously instead of stopping between samples.
 * Shared by every display. This is the needle feel confirmed on the bike; stiffer springs and an
 * interpolated target were tried on 2026-11-26 and felt worse.
 */
internal val ReadingSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/**
 * The tachometer position (thousands of RPM) and the speed as a display draws them this frame.
 * They are read on demand: reading them while drawing redraws only what uses them each frame,
 * instead of recomposing the whole display. A missing reading reads as null.
 */
@Stable
internal class AnimatedReadings(val tachometer: () -> Float?, val speedKmh: () -> Int?)

/**
 * Moves the tachometer needle toward [readings] with the rider's needle spring
 * ([LocalNeedleSettings], by default the same as [ReadingSpring]) and the speed with
 * [ReadingSpring]. A missing reading stays null, so displays show it as missing instead of
 * animating toward zero.
 */
@Composable
internal fun animateReadings(readings: DashboardReadings): AnimatedReadings {
    val needle = LocalNeedleSettings.current
    val needleSpring = remember(needle) { spring<Float>(dampingRatio = needle.dampingRatio, stiffness = needle.stiffness) }
    val tachometer = animateFloatAsState(TachometerScale.fromRpm(readings.rpm), needleSpring, label = "rpm")
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
