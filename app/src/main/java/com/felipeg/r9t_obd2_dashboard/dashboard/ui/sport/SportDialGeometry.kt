package com.felipeg.r9t_obd2_dashboard.dashboard.ui.sport

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.TachometerScale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Maps tachometer values to angles on the sport dial. The scale opens slightly below horizontal
 * on both sides. Angles are clockwise from 3 o'clock, as Compose draws arcs.
 */
internal object SportDialAngles {
    const val START = 171f
    const val SWEEP = 198f

    fun of(thousands: Float): Float = START + SWEEP * thousands / TachometerScale.MAX

    fun sweep(from: Float, to: Float): Float = SWEEP * (to - from) / TachometerScale.MAX

    /** The point at [radius] from [center] in the direction of [thousands] on the scale. */
    fun point(center: Offset, radius: Float, thousands: Float): Offset {
        val radians = Math.toRadians(of(thousands).toDouble())
        return Offset(center.x + radius * cos(radians).toFloat(), center.y + radius * sin(radians).toFloat())
    }
}

/**
 * Where the dial sits inside the instrument area between the TFT bars, in dp. Shared by the
 * canvas and the readouts drawn over it.
 */
internal data class SportDialLayout(val centerX: Dp, val centerY: Dp, val radius: Dp) {
    companion object {
        // Room for the needle, which overshoots the arc by ~5% of the radius.
        private val TOP_MARGIN = 20.dp
        private val BOTTOM_MARGIN = 2.dp

        /** The arc rises a full radius above its center and dips ~0.16 radius below it. */
        private const val HEIGHT_IN_RADII = 1.18f
        private const val MAX_WIDTH_SHARE = .36f

        fun fit(width: Dp, height: Dp): SportDialLayout {
            val radius = min(width * MAX_WIDTH_SHARE, (height - TOP_MARGIN - BOTTOM_MARGIN) / HEIGHT_IN_RADII)
            return SportDialLayout(width / 2, TOP_MARGIN + radius, radius)
        }
    }
}

/** One lit piece of the tachometer scale, in thousands of RPM. */
internal data class ScaleSegment(val from: Float, val to: Float, val limitZone: Boolean)

/**
 * Splits the always-lit scale band like the original sport dial: white pieces separated by a
 * small gap at every 1,000 rpm, then narrower red stripes every 250 rpm in the limit zone.
 */
internal object SportScaleSegments {
    /** Gap between pieces, in thousands of RPM. */
    const val GAP = .07f
    private const val NORMAL_STEP = 1f
    private const val LIMIT_STEP = .25f
    private const val EPSILON = 1e-4f

    fun build(
        limitStart: Float = TachometerScale.LIMIT_START,
        max: Float = TachometerScale.MAX,
    ): List<ScaleSegment> =
        divide(0f, limitStart, NORMAL_STEP, limitZone = false) + divide(limitStart, max, LIMIT_STEP, limitZone = true)

    private fun divide(from: Float, to: Float, step: Float, limitZone: Boolean) = buildList {
        var start = from
        while (start < to - EPSILON) {
            val end = minOf((kotlin.math.floor(start / step + EPSILON) + 1) * step, to)
            add(ScaleSegment(start + GAP / 2, end - GAP / 2, limitZone))
            start = end
        }
    }
}
