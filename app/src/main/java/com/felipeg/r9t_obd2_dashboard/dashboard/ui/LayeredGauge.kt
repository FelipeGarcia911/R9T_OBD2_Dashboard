package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density

/**
 * How one display draws its gauge (the Strategy in [LayeredGauge]): what never changes, and what
 * follows the reading. Each display brings its own; the layering and caching stay in one place.
 */
internal interface GaugeStyle {
    /** Measures, paths and paints for [size], built once per size and reused every frame. */
    fun prepare(density: Density, size: Size): PreparedGauge
}

internal interface PreparedGauge {
    /** The scale, ticks, numerals and anything else that does not move. */
    fun DrawScope.drawStatic()

    /** What shows [thousands], the reading on the scale in thousands of RPM. */
    fun DrawScope.drawReading(thousands: Float)
}

/**
 * Draws a [GaugeStyle] in two layers: the static part once into a cached GPU layer, and the
 * reading over it. [reading] is read while drawing, so a moving reading redraws only the top
 * layer each frame and never recomposes the display. Null draws no reading.
 */
@Composable
internal fun LayeredGauge(style: GaugeStyle, reading: () -> Float?, modifier: Modifier = Modifier) {
    val probe = LocalRenderProbe.current
    Box(modifier) {
        Spacer(
            Modifier.matchParentSize().staticLayer().drawWithCache {
                val gauge = style.prepare(this, size)
                onDrawBehind {
                    probe.drawn(RenderLayer.SCALE)
                    with(gauge) { drawStatic() }
                }
            },
        )
        Spacer(
            Modifier.matchParentSize().movingLayer().drawWithCache {
                val gauge = style.prepare(this, size)
                onDrawBehind {
                    probe.drawn(RenderLayer.READING)
                    reading()?.let { with(gauge) { drawReading(it) } }
                }
            },
        )
    }
}
