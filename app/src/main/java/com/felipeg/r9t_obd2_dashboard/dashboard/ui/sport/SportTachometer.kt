package com.felipeg.r9t_obd2_dashboard.dashboard.ui.sport

import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftColors
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftTypography
import android.graphics.Paint
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Density
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.GaugeStyle
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.LayeredGauge
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.PreparedGauge
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.TachometerScale
import androidx.compose.ui.res.stringResource
import com.felipeg.r9t_obd2_dashboard.R

private const val TICKS_PER_THOUSAND = 4

/**
 * Semicircular sport tachometer: the scale, ticks and numerals stay still while the needle
 * follows [thousands], drawn by [LayeredGauge] so only the needle redraws as the reading moves.
 * Null hides the needle.
 */
@Composable
internal fun SportTachometer(thousands: () -> Float?, layout: SportDialLayout, modifier: Modifier = Modifier) {
    val numerals = TftTypography.rememberNumeralPaint().apply { textAlign = Paint.Align.CENTER }
    val description = stringResource(R.string.dashboard_tachometer_description)
    val style = remember(layout, numerals) { SportGaugeStyle(layout, numerals) }
    LayeredGauge(style, thousands, modifier.semantics { contentDescription = description })
}

private class SportGaugeStyle(private val layout: SportDialLayout, private val numerals: Paint) : GaugeStyle {
    override fun prepare(density: Density, size: Size): PreparedGauge {
        val dial = with(density) {
            Dial(center = Offset(layout.centerX.toPx(), layout.centerY.toPx()), radius = layout.radius.toPx())
        }
        return object : PreparedGauge {
            override fun DrawScope.drawStatic() {
                drawScale(dial)
                drawTicks(dial)
                drawNumerals(dial, numerals)
            }

            override fun DrawScope.drawReading(thousands: Float) = drawNeedle(dial, thousands)
        }
    }
}

/** Pixel measurements of the dial, all proportional to its radius. */
private class Dial(val center: Offset, val radius: Float) {
    val band = radius * .04f
    val limitBand = radius * .08f
    val outerEdge = radius + band / 2
    val tickOuter = radius - band / 2 - radius * .025f
}

private val ScaleSegments = SportScaleSegments.build()

/**
 * The always-lit scale band: white pieces split at every 1,000 rpm, then wider red stripes in
 * the limit zone, flush with the outer edge. The needle, not the band, shows the reading.
 */
private fun DrawScope.drawScale(dial: Dial) {
    ScaleSegments.forEach { segment ->
        val width = if (segment.limitZone) dial.limitBand else dial.band
        drawBand(dial, segment.from, segment.to, dial.outerEdge - width / 2, width,
            if (segment.limitZone) TftColors.Red else TftColors.White)
    }
}

private fun DrawScope.drawBand(dial: Dial, from: Float, to: Float, radius: Float, width: Float, color: Color) {
    drawArc(
        color = color,
        startAngle = SportDialAngles.of(from),
        sweepAngle = SportDialAngles.sweep(from, to),
        useCenter = false,
        topLeft = Offset(dial.center.x - radius, dial.center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = width),
    )
}

/** Ticks every 250 rpm, longer and brighter every 1,000. */
private fun DrawScope.drawTicks(dial: Dial) {
    val steps = TachometerScale.MAX.toInt() * TICKS_PER_THOUSAND
    for (step in 0..steps) {
        val value = step.toFloat() / TICKS_PER_THOUSAND
        val major = step % TICKS_PER_THOUSAND == 0
        val length = dial.radius * if (major) .085f else .045f
        val color = when {
            TachometerScale.isInLimitZone(value) -> TftColors.Red
            major -> TftColors.White
            else -> TftColors.TickDim
        }
        drawLine(
            color,
            SportDialAngles.point(dial.center, dial.tickOuter, value),
            SportDialAngles.point(dial.center, dial.tickOuter - length, value),
            strokeWidth = dial.radius * if (major) .016f else .009f,
        )
    }
}

/** Italic numerals inside the ticks. Zero is drawn larger, as on the original sport dial. */
private fun DrawScope.drawNumerals(dial: Dial, paint: Paint) = drawIntoCanvas { canvas ->
    for (thousand in 0..TachometerScale.MAX.toInt()) {
        val zero = thousand == 0
        paint.textSize = dial.radius * if (zero) .24f else .135f
        paint.color = (if (TachometerScale.isInLimitZone(thousand.toFloat())) TftColors.Red
            else TftColors.White).toArgb()
        val inset = dial.radius * if (zero) .26f else .2f
        val point = SportDialAngles.point(dial.center, dial.tickOuter - inset, thousand.toFloat())
        val baseline = point.y - (paint.ascent() + paint.descent()) / 2
        canvas.nativeCanvas.drawText(thousand.toString(), point.x, baseline, paint)
    }
}

/** A white bar from inside the ticks out through the band, as at rest on the original dial. */
private fun DrawScope.drawNeedle(dial: Dial, thousands: Float) {
    val inner = SportDialAngles.point(dial.center, dial.tickOuter - dial.radius * .15f, thousands)
    val outer = SportDialAngles.point(dial.center, dial.outerEdge + dial.radius * .02f, thousands)
    drawLine(TftColors.GroundEdge, inner, outer, dial.radius * .05f, StrokeCap.Butt)
    drawLine(TftColors.White, inner, outer, dial.radius * .03f, StrokeCap.Butt)
}
