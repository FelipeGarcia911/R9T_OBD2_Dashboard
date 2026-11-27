package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.felipeg.r9t_obd2_dashboard.dashboard.needle.TachometerNeedle
import kotlin.math.roundToInt

/**
 * Settles in ~100 ms with no overshoot, for the speed digits: a critically damped spring keeps its
 * velocity when the target changes, so the digits count continuously instead of jumping.
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
 * Moves the tachometer needle like a real one ([TachometerNeedle]) and the speed toward
 * [readings] with [ReadingSpring]. A missing reading stays null, so displays show it as missing
 * instead of animating toward zero.
 */
@Composable
internal fun animateReadings(readings: DashboardReadings): AnimatedReadings {
    val needle = remember { TachometerNeedle() }
    val needlePosition = remember { mutableStateOf<Float?>(null) }
    val rpm = readings.rpm
    // Keyed on the value: the ECU repeats each RPM value several times, and repeats are not news.
    LaunchedEffect(rpm) {
        needle.onReading(System.nanoTime() / 1_000_000, rpm?.let { TachometerScale.fromRpm(it) })
    }
    LaunchedEffect(needle) {
        while (true) {
            withFrameNanos { frameNanos ->
                needlePosition.value = needle.advance(frameNanos / 1_000_000)?.coerceIn(0f, TachometerScale.MAX)
            }
        }
    }

    val speed = animateFloatAsState((readings.speedKmh ?: 0.0).toFloat(), ReadingSpring, label = "speed")
    val hasSpeed = readings.speedKmh != null
    return remember(hasSpeed, speed) {
        AnimatedReadings(
            tachometer = { needlePosition.value },
            speedKmh = { if (hasSpeed) speed.value.roundToInt() else null },
        )
    }
}
