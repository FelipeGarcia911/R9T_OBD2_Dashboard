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

/** Where the dial sits on screen, in dp. Shared by the canvas and the readouts on top of it. */
internal data class SportDialLayout(val centerX: Dp, val centerY: Dp, val radius: Dp) {
    companion object {
        private val TOP_MARGIN = 20.dp
        private val BOTTOM_ROW = 72.dp

        /** The arc rises a full radius above its center and dips ~0.16 radius below it. */
        private const val HEIGHT_IN_RADII = 1.18f
        private const val MAX_WIDTH_SHARE = .40f

        fun fit(width: Dp, height: Dp): SportDialLayout {
            val radius = min(width * MAX_WIDTH_SHARE, (height - BOTTOM_ROW) / HEIGHT_IN_RADII)
            return SportDialLayout(width / 2, TOP_MARGIN + radius, radius)
        }
    }
}
