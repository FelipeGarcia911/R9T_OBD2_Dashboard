package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Settles in ~200 ms with no overshoot. Readings arrive every ~80 ms; a critically damped spring
 * keeps its velocity when the target changes, so gauges and digits move continuously instead of
 * stopping between samples. Shared by every display.
 */
internal val ReadingSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
