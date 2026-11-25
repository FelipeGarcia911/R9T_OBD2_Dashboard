package com.felipeg.r9t_obd2_dashboard.dashboard.ui.classic

import android.graphics.Paint
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.GaugeStyle
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.LayeredGauge
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.PreparedGauge
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.TachometerScale
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftColors
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftTypography
import androidx.compose.ui.res.stringResource
import com.felipeg.r9t_obd2_dashboard.R

private val SegmentBounds = ClassicScaleSegments.bounds()

/** Dashes per 1,000 rpm on the inner line of the rail. */
private const val DASHES_PER_THOUSAND = 4

/** Share of each dash interval left open. */
private const val DASH_GAP = .22f

/** Numerals are a lighter weight than the speed digits, as on the original scale. */
private const val NUMERAL_WEIGHT = 560

/**
 * The classic bar tachometer: straight-sided grey segments climbing the left side and running
 * across the top, lit from zero to the reading, red from the limit zone on, with a rail along
 * the outside and italic numerals beyond it. Drawn by [LayeredGauge]: the dark scale, rail and
 * numerals stay still while only the lit segments follow [thousands]; null leaves them all dark.
 */
@Composable
internal fun ClassicTachometer(thousands: () -> Float?, layout: ClassicDialLayout, modifier: Modifier = Modifier) {
    val numerals = TftTypography.rememberNumeralPaint(NUMERAL_WEIGHT).apply { textAlign = Paint.Align.CENTER }
    val description = stringResource(R.string.dashboard_tachometer_description)
    val style = remember(layout, numerals) { ClassicGaugeStyle(layout, numerals) }
    LayeredGauge(style, thousands, modifier.semantics { contentDescription = description })
}

/** A whole segment's lit paths, built once per size. */
private class LitSegment(val start: Float, val end: Float, val face: Path, val shade: Path, val paint: SegmentPaint)

private class ClassicGaugeStyle(private val layout: ClassicDialLayout, private val numerals: Paint) : GaugeStyle {
    override fun prepare(density: Density, size: Size): PreparedGauge {
        val scale = ScaleBand(ClassicScalePath(layout.scaleEnd), unit = with(density) { layout.unit.toPx() })
        val left = with(density) { layout.left.toPx() }
        val lit = SegmentBounds.zipWithNext().map { (start, end) ->
            val paint = if (TachometerScale.isInLimitZone(start)) LimitLit else Lit
            LitSegment(start, end, scale.area(start, end), scale.area(start, end, outer = scale.shadeEdge), paint)
        }
        return object : PreparedGauge {
            override fun DrawScope.drawStatic() = translate(left = left) {
                SegmentBounds.zipWithNext().forEach { (start, end) ->
                    drawSegment(scale, start, end, if (TachometerScale.isInLimitZone(start)) LimitDim else Dim)
                }
                drawCuts(scale, upTo = TachometerScale.MAX)
                drawRail(scale)
                drawNumerals(scale, numerals)
            }

            /** Whole segments from the cached paths; only the one the reading ends in is built. */
            override fun DrawScope.drawReading(thousands: Float) = translate(left = left) {
                for (segment in lit) {
                    if (thousands <= segment.start) break
                    if (thousands >= segment.end) {
                        drawPath(segment.face, segment.paint.face)
                        drawPath(segment.shade, segment.paint.shade)
                    } else {
                        drawSegment(scale, segment.start, thousands, segment.paint)
                    }
                }
                drawCuts(scale, upTo = thousands)
            }
        }
    }
}

/** The scale's centerline scaled to pixels, with the band's width and the rail's spacing. */
private class ScaleBand(private val path: ClassicScalePath, private val unit: Float) {
    val halfWidth = unit * .08f
    /** Where the darker inner strip of each segment ends: it covers the inner 35% of the band. */
    val shadeEdge = -halfWidth * .3f
    val railInner = halfWidth + unit * .009f
    val railOuter = halfWidth + unit * .022f
    val stroke = unit * .0028f
    val gap = unit * .0065f

    fun edge(mark: Float, offset: Float, slanted: Boolean = true): Offset =
        path.edge(mark, offset / unit, if (slanted) path.shear(mark) else 0f) * unit

    /**
     * A point [offset] out from the centerline, in line with the segment cut at [mark]: it
     * starts where the leaning cut meets the outer edge and continues straight outward, so the
     * rail notches and numerals stay in line with the cuts.
     */
    fun outside(mark: Float, offset: Float): Offset =
        edge(mark, offset, slanted = false) + (edge(mark, halfWidth) - edge(mark, halfWidth, slanted = false))

    /**
     * The band between [start] and [end] and between the [outer] and [inner] offsets, turning
     * sharply at the corners and cut along the leaning segment lines at both ends.
     */
    fun area(start: Float, end: Float, outer: Float = halfWidth, inner: Float = -halfWidth): Path = Path().apply {
        val marks = listOf(start) + path.corners(start, end) + end
        marks.forEachIndexed { index, mark ->
            val point = edge(mark, outer)
            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
        marks.asReversed().forEach { mark ->
            val point = edge(mark, inner)
            lineTo(point.x, point.y)
        }
        close()
    }

    /** An open line outside the band between [start] and [end], sharp at the corners. */
    fun line(start: Float, end: Float, offset: Float): Path = Path().apply {
        (listOf(start) + path.corners(start, end) + end).forEachIndexed { index, mark ->
            val point = outside(mark, offset)
            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
    }

    /** A line through the band at [mark], [overshoot] past both edges. */
    fun cut(mark: Float, overshoot: Float): Pair<Offset, Offset> =
        edge(mark, halfWidth + overshoot) to edge(mark, -halfWidth - overshoot)
}

private class SegmentPaint(val face: Color, val shade: Color)

private val Lit = SegmentPaint(TftColors.SegmentLit, TftColors.SegmentLitShade)
private val Dim = SegmentPaint(TftColors.SegmentDim, TftColors.SegmentDimShade)
private val LimitLit = SegmentPaint(TftColors.Red, TftColors.RedShade)
private val LimitDim = SegmentPaint(TftColors.RedTrack, TftColors.RedTrackShade)

/**
 * The black cuts between segments, up to [upTo]: over the dark scale for the whole range, and
 * again over the lit part so the gaps stay open where the reading has lit the segments.
 */
private fun DrawScope.drawCuts(scale: ScaleBand, upTo: Float) {
    clipPath(scale.area(0f, TachometerScale.MAX)) {
        SegmentBounds.drop(1).dropLast(1).forEach { mark ->
            if (mark > upTo) return@clipPath
            val (outer, inner) = scale.cut(mark, scale.gap)
            drawLine(TftColors.GroundEdge, outer, inner, strokeWidth = scale.gap)
        }
    }
}

/** Each segment is two-tone: a lighter face toward the outside and a darker strip on the inside. */
private fun DrawScope.drawSegment(scale: ScaleBand, start: Float, end: Float, paint: SegmentPaint) {
    drawPath(scale.area(start, end), paint.face)
    drawPath(scale.area(start, end, outer = scale.shadeEdge), paint.shade)
}

/**
 * The thin accent along the outside of the band: a solid outer line, a dashed inner line and a
 * notch joining them at every 1,000 rpm, white along the usable range and red over the limit.
 */
private fun DrawScope.drawRail(scale: ScaleBand) {
    val limit = TachometerScale.LIMIT_START
    val max = TachometerScale.MAX
    val style = Stroke(scale.stroke, join = StrokeJoin.Miter)
    fun color(mark: Float) = if (TachometerScale.isInLimitZone(mark)) TftColors.Red else TftColors.White
    drawPath(scale.line(0f, limit, scale.railOuter), TftColors.White, style = style)
    drawPath(scale.line(limit, max, scale.railOuter), TftColors.Red, style = style)
    val step = 1f / DASHES_PER_THOUSAND
    for (index in 0 until (max * DASHES_PER_THOUSAND).toInt()) {
        val start = index * step
        drawPath(scale.line(start, start + step * (1 - DASH_GAP), scale.railInner), color(start), style = style)
    }
    for (thousand in 0..max.toInt()) {
        val mark = thousand.toFloat()
        drawLine(color(mark), scale.outside(mark, scale.railInner), scale.outside(mark, scale.railOuter), scale.stroke)
    }
}

/** Italic numerals outside the rail at every 1,000 rpm, red in the limit zone. */
private fun DrawScope.drawNumerals(scale: ScaleBand, paint: Paint) = drawIntoCanvas { canvas ->
    paint.textSize = scale.halfWidth * 1.1f
    val spacing = scale.railOuter + paint.textSize * .62f
    for (thousand in 0..TachometerScale.MAX.toInt()) {
        val mark = thousand.toFloat()
        paint.color = (if (TachometerScale.isInLimitZone(mark)) TftColors.Red else TftColors.White).toArgb()
        val point = scale.outside(mark, spacing)
        val baseline = point.y - (paint.ascent() + paint.descent()) / 2
        canvas.nativeCanvas.drawText(thousand.toString(), point.x, baseline, paint)
    }
}
