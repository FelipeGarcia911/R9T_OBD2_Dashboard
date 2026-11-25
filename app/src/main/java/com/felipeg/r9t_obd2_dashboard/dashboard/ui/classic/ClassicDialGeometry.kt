package com.felipeg.r9t_obd2_dashboard.dashboard.ui.classic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.TachometerScale
import kotlin.math.abs

/**
 * Where the classic instrument sits inside the area between the TFT bars, in dp. Shapes are
 * measured in units of the area's height, so the scale keeps its proportions on any phone; the
 * horizontal run of the scale stretches to the width it is given, up to [MAX_ASPECT].
 */
internal data class ClassicDialLayout(val left: Dp, val width: Dp, val unit: Dp) {
    /** Width of the instrument in height units. */
    val aspect: Float get() = width / unit

    /** Where the scale's centerline ends, at the top of the range, in height units. */
    val scaleEnd: Float get() = aspect - GEAR_COLUMN

    /** Center of the speed digits, in height units from the instrument's top left. */
    val speedCenter: Offset get() = Offset(aspect * SPEED_X_SHARE, READOUT_Y)

    /** Center of the gear indicator, in the column right of the scale's end. */
    val gearCenter: Offset get() = Offset(aspect - GEAR_COLUMN / 2, READOUT_Y)

    companion object {
        /** Wider screens leave black margins on both sides rather than stretch the scale further. */
        const val MAX_ASPECT = 2.6f
        const val GEAR_COLUMN = .5f
        private const val SPEED_X_SHARE = .53f
        private const val READOUT_Y = .6f

        fun fit(width: Dp, height: Dp): ClassicDialLayout {
            val instrumentWidth = min(width, height * MAX_ASPECT)
            return ClassicDialLayout(left = (width - instrumentWidth) / 2, width = instrumentWidth, unit = height)
        }
    }
}

/**
 * Where the classic scale splits into segments, in thousands of RPM: every 1,000 rpm, and
 * always at the start of the limit zone.
 */
internal object ClassicScaleSegments {
    fun bounds(
        limitStart: Float = TachometerScale.LIMIT_START,
        max: Float = TachometerScale.MAX,
    ): List<Float> = ((0..max.toInt()).map { it.toFloat() } + limitStart + max)
        .filter { it <= max }
        .distinct()
        .sorted()
}

/**
 * The centerline of the classic scale, in height units: a polygon of straight runs with sharp
 * corners. From zero it climbs diagonally up and left, rises straight up the left side, cuts
 * across a chamfer to 3,000 rpm, eases into the top and runs straight to the end of the range.
 *
 * Offsets from the centerline are positive toward the outside, where the numerals sit. At a
 * corner the offset follows the miter, so parallel lines keep their corners sharp.
 */
internal class ClassicScalePath(scaleEnd: Float) {
    private val knots = listOf(
        0f to Offset(.334f, .835f),
        .92f to Offset(.203f, .72f),
        2.5f to Offset(.201f, .416f),
        3f to Offset(.325f, .342f),
        3.95f to Offset(.544f, .301f),
        TachometerScale.MAX to Offset(scaleEnd, .301f),
    )

    /** The centerline point for [mark] thousands of RPM, clamped to the scale. */
    fun center(mark: Float): Offset {
        val clamped = mark.coerceIn(0f, TachometerScale.MAX)
        val index = runIndex(clamped)
        val (from, start) = knots[index]
        val (to, end) = knots[index + 1]
        return start + (end - start) * ((clamped - from) / (to - from))
    }

    /** Unit vector toward higher RPM along the straight run that holds [mark]. */
    fun tangent(mark: Float): Offset = runDirection(runIndex(mark.coerceIn(0f, TachometerScale.MAX)))

    /** Corners strictly between [start] and [end], where outlines must turn. */
    fun corners(start: Float, end: Float): List<Float> =
        knots.subList(1, knots.lastIndex).map { it.first }.filter { it > start + EPSILON && it < end - EPSILON }

    /**
     * The point [offset] away from the centerline at [mark], outward when positive. [shear] tilts
     * the cut toward higher RPM on the outside, as the segments lean along the top run.
     */
    fun edge(mark: Float, offset: Float, shear: Float = 0f): Offset {
        val corner = knots.subList(1, knots.lastIndex).indexOfFirst { abs(it.first - mark) < EPSILON }
        if (corner >= 0) {
            val before = outward(runDirection(corner))
            val after = outward(runDirection(corner + 1))
            val miter = (before + after) / (before + after).getDistance()
            return knots[corner + 1].second + miter * (offset / (miter.x * before.x + miter.y * before.y))
        }
        val tangent = tangent(mark)
        return center(mark) + outward(tangent) * offset + tangent * (offset * shear)
    }

    /** How far segment cuts lean at [mark]: upright up to 5,000 rpm, then leaning more to the end. */
    fun shear(mark: Float): Float = ((mark - LEAN_FROM) / (TachometerScale.MAX - LEAN_FROM)).coerceIn(0f, 1f) * MAX_SHEAR

    private fun runIndex(mark: Float): Int =
        knots.indexOfLast { (at, _) -> at <= mark }.coerceIn(0, knots.lastIndex - 1)

    private fun runDirection(index: Int): Offset {
        val direction = knots[index + 1].second - knots[index].second
        return direction / direction.getDistance()
    }

    private fun outward(tangent: Offset) = Offset(tangent.y, -tangent.x)

    private companion object {
        const val EPSILON = 1e-4f
        const val LEAN_FROM = 5f
        const val MAX_SHEAR = .85f
    }
}
