package com.felipeg.r9t_obd2_dashboard.dashboard.ui.sport

import android.graphics.Paint
import androidx.compose.foundation.Canvas
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

private const val TICKS_PER_THOUSAND = 4

/**
 * Semicircular sport tachometer. [thousands] is the animated reading on the scale;
 * null hides the needle and leaves the scale unlit.
 */
@Composable
internal fun SportTachometer(thousands: Float?, layout: SportDialLayout, modifier: Modifier = Modifier) {
    val numerals = SportTypography.rememberNumeralPaint().apply { textAlign = Paint.Align.CENTER }
    Canvas(modifier.semantics { contentDescription = "Tachometer, zero to ten thousand RPM" }) {
        val dial = Dial(
            center = Offset(layout.centerX.toPx(), layout.centerY.toPx()),
            radius = layout.radius.toPx(),
        )
        drawScale(dial, thousands)
        drawTicks(dial)
        drawNumerals(dial, numerals)
        if (thousands != null) drawNeedle(dial, thousands)
    }
}

/** Pixel measurements of the dial, all proportional to its radius. */
private class Dial(val center: Offset, val radius: Float) {
    val band = radius * .055f
    val tickOuter = radius - band / 2 - radius * .02f
}

/** Dark track with a dim red limit zone, lit up to the current reading. */
private fun DrawScope.drawScale(dial: Dial, thousands: Float?) {
    val limit = TachometerScale.LIMIT_START
    drawBand(dial, 0f, limit, SportColors.Track)
    drawBand(dial, limit, TachometerScale.MAX, SportColors.RedTrack)
    if (thousands == null || thousands <= 0f) return
    drawBand(dial, 0f, minOf(thousands, limit), SportColors.White)
    drawBand(dial, limit, thousands, SportColors.Red)
}

private fun DrawScope.drawBand(dial: Dial, from: Float, to: Float, color: Color) {
    if (to <= from) return
    drawArc(
        color = color,
        startAngle = SportDialAngles.of(from),
        sweepAngle = SportDialAngles.sweep(from, to),
        useCenter = false,
        topLeft = Offset(dial.center.x - dial.radius, dial.center.y - dial.radius),
        size = Size(dial.radius * 2, dial.radius * 2),
        style = Stroke(width = dial.band),
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
            TachometerScale.isInLimitZone(value) -> SportColors.Red
            major -> SportColors.White
            else -> SportColors.TickDim
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
        paint.color = (if (TachometerScale.isInLimitZone(thousand.toFloat())) SportColors.Red
            else SportColors.White).toArgb()
        val inset = dial.radius * if (zero) .26f else .2f
        val point = SportDialAngles.point(dial.center, dial.tickOuter - inset, thousand.toFloat())
        val baseline = point.y - (paint.ascent() + paint.descent()) / 2
        canvas.nativeCanvas.drawText(thousand.toString(), point.x, baseline, paint)
    }
}

/** A solid bar across the band, red inside the limit zone. */
private fun DrawScope.drawNeedle(dial: Dial, thousands: Float) {
    val color = if (TachometerScale.isInLimitZone(thousands)) SportColors.Red else SportColors.White
    val inner = SportDialAngles.point(dial.center, dial.radius - dial.band / 2 - dial.radius * .11f, thousands)
    val outer = SportDialAngles.point(dial.center, dial.radius + dial.band / 2 + dial.radius * .015f, thousands)
    drawLine(color, inner, outer, dial.radius * .024f, StrokeCap.Butt)
}
