package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

private const val SCALE_END = 10f

// BMW Motorrad's R nineT Scrambler rider's manual lists a maximum of 8,500 rpm.
private const val RED_ZONE_START = 8.5f
private const val SEGMENT_COUNT = 20
private val GaugeFill = Color(0xFFD4E3D9)
private val GaugeHot = Color(0xFFFF3A32)
private val GaugeTrack = Color(0xFF26383F)
private val GaugeHotTrack = Color(0xFF51272A)
private val GaugeOutline = Color(0xFF8A9E9B)

@Composable
internal fun RpmGauge(rpm: Double?, modifier: Modifier = Modifier) {
    val fill by animateFloatAsState(
        targetValue = ((rpm ?: 0.0) / 1_000).toFloat().coerceIn(0f, SCALE_END),
        animationSpec = tween(durationMillis = 90),
        label = "tachometer fill",
    )
    val labels = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
        }
    }

    Canvas(modifier.semantics { contentDescription = "Tachometer scale, zero to ten thousand RPM" }) {
        if (size.width == 0f || size.height == 0f) return@Canvas
        val band = min(size.height * .22f, size.width * .09f)
        val curve = GaugeCurve(size.width, size.height, band)
        val visibleFill = if (rpm == null) 0f else fill
        val span = SCALE_END / SEGMENT_COUNT
        val outlinePadding = 2.dp.toPx()

        repeat(SEGMENT_COUNT) { index ->
            val start = index * span
            val end = (index + 1) * span
            val hot = start >= RED_ZONE_START
            drawPath(curve.band(start, end), if (hot) GaugeHotTrack else GaugeTrack)
            val filledEnd = min(visibleFill, end)
            if (filledEnd > start) {
                drawPath(curve.band(start, filledEnd), if (hot) GaugeHot else GaugeFill)
            }
        }

        val separatorWidth = 1.6.dp.toPx()
        clipPath(curve.band(0f, SCALE_END)) {
            for (index in 1 until SEGMENT_COUNT) {
                val (outer, inner) = curve.cut(index * span, separatorWidth)
                drawLine(Color.Black, outer, inner, strokeWidth = separatorWidth)
            }
        }
        drawPath(curve.band(0f, SCALE_END, outlinePadding), GaugeOutline, style = Stroke(1.dp.toPx()))

        labels.textSize = min(15.sp.toPx(), size.width * .034f)
        drawIntoCanvas { canvas ->
            for (tick in 0..10) {
                val mark = tick.toFloat()
                val point = curve.label(mark, outlinePadding + labels.textSize * .75f)
                labels.color = (if (tick >= RED_ZONE_START) GaugeHot else Color(0xFFF5F9F7)).toArgb()
                canvas.nativeCanvas.drawText(
                    tick.toString(), point.x, point.y - (labels.ascent() + labels.descent()) / 2, labels,
                )
            }
            labels.color = Color(0xFFB7C6CB).toArgb()
            labels.textSize = min(10.sp.toPx(), size.width * .023f)
            canvas.nativeCanvas.drawText("RPM × 1000", size.width * .15f, size.height * .985f, labels)
        }
    }
}

@Preview(widthDp = 700, heightDp = 250)
@Composable
private fun TachometerPreview() {
    RpmGauge(8_450.0, Modifier.fillMaxSize().background(Color.Black))
}

private class GaugeCurve(
    private val width: Float,
    private val height: Float,
    private val fullBand: Float,
) {
    fun band(start: Float, end: Float, padding: Float = 0f): Path {
        val slices = ((end - start) * 16).toInt().coerceAtLeast(3)
        return Path().apply {
            for (index in 0..slices) {
                val mark = start + (end - start) * index / slices
                val point = edge(mark, -(halfBand(mark) + padding), slant = true)
                if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
            for (index in slices downTo 0) {
                val mark = start + (end - start) * index / slices
                val point = edge(mark, halfBand(mark) + padding, slant = true)
                lineTo(point.x, point.y)
            }
            if (start == 0f) {
                val radius = halfBand(0f) + padding
                val left = edge(0f, radius)
                val right = edge(0f, -radius)
                val shoulder = radius * 1.25f
                cubicTo(left.x, left.y + shoulder, right.x, right.y + shoulder, right.x, right.y)
            }
            close()
        }
    }

    fun cut(mark: Float, extension: Float): Pair<Offset, Offset> {
        val radius = halfBand(mark) + extension
        return edge(mark, radius, slant = true) to edge(mark, -radius, slant = true)
    }

    fun label(mark: Float, spacing: Float): Offset {
        val border = edge(mark, halfBand(mark), slant = true)
        return border + (edge(mark, spacing) - point(mark))
    }

    private fun halfBand(mark: Float): Float {
        val progress = ((mark - 1.5f) / 2.5f).coerceIn(0f, 1f)
        return fullBand * (.34f + .16f * progress * progress * (3f - 2f * progress))
    }

    private fun edge(mark: Float, offset: Float, slant: Boolean = false): Offset {
        val center = point(mark)
        val direction = point((mark + .01f).coerceAtMost(SCALE_END)) -
            point((mark - .01f).coerceAtLeast(0f))
        val tangent = direction / direction.getDistance()
        val shear = if (slant) ((mark - 4f) * .8f).coerceIn(0f, .55f) else 0f
        return center + Offset(tangent.y, -tangent.x) * offset + tangent * (offset * shear)
    }

    private fun point(mark: Float): Offset {
        val corner = when {
            mark < 2.5f -> 2f
            mark < 3.5f -> 3f
            else -> 4f
        }
        val radius = .26f
        if (mark !in (corner - radius)..(corner + radius)) return straightPoint(mark)

        // Round only the joints between the straight portions of the scale.
        val t = (mark - (corner - radius)) / (2f * radius)
        val u = 1f - t
        return straightPoint(corner - radius) * (u * u) +
            straightPoint(corner) * (2f * u * t) +
            straightPoint(corner + radius) * (t * t)
    }

    private fun straightPoint(mark: Float): Offset {
        val normalized = when {
            mark <= 2f -> Offset(.14f, .82f - mark * .165f)
            mark <= 3f -> Offset(.14f, .49f) + Offset(.06f, -.177f) * (mark - 2f)
            mark <= 4f -> Offset(.20f, .313f) + Offset(.12f, -.073f) * (mark - 3f)
            else -> Offset(.32f + (mark - 4f) * .099f, .24f)
        }
        return Offset(normalized.x * width, normalized.y * height)
    }
}
