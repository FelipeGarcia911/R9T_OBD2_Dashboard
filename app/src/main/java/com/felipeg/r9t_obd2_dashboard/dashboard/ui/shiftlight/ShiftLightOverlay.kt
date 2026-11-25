package com.felipeg.r9t_obd2_dashboard.dashboard.ui.shiftlight

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.felipeg.r9t_obd2_dashboard.dashboard.shiftlight.ShiftLightPhase
import com.felipeg.r9t_obd2_dashboard.dashboard.shiftlight.ShiftLightRules
import com.felipeg.r9t_obd2_dashboard.dashboard.shiftlight.ShiftLightSettings
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftColors

/** How the full-screen glow looks in each lit phase. */
private class GlowStyle(val color: Color, val alpha: Float, val depth: Dp)

private val Approaching = GlowStyle(TftColors.Amber, alpha = .55f, depth = 26.dp)
private val Shift = GlowStyle(TftColors.Red, alpha = .95f, depth = 44.dp)

/**
 * The shift light across the whole screen: a glow along every edge, fading toward the middle so
 * the readings stay legible. Steady amber as the shift point nears, flashing red at it. Draws
 * nothing while off and never takes touches.
 */
@Composable
internal fun ShiftLightOverlay(phase: ShiftLightPhase, frequencyHz: Int, modifier: Modifier = Modifier) {
    val lit by rememberShiftLightLit(phase, frequencyHz)
    if (phase == ShiftLightPhase.OFF) return
    val style = if (phase == ShiftLightPhase.SHIFT) Shift else Approaching
    Canvas(modifier.semantics { contentDescription = if (phase == ShiftLightPhase.SHIFT) "Shift up" else "Shift point near" }) {
        if (lit) drawEdgeGlow(style)
    }
}

/**
 * Whether the light is on right now. Ticks with the frame clock only while flashing, so a steady
 * or dark light costs nothing.
 */
@Composable
internal fun rememberShiftLightLit(phase: ShiftLightPhase, frequencyHz: Int): State<Boolean> =
    produceState(ShiftLightRules.isLit(phase, frequencyHz, 0), phase, frequencyHz) {
        value = ShiftLightRules.isLit(phase, frequencyHz, 0)
        if (phase != ShiftLightPhase.SHIFT || frequencyHz == 0) return@produceState
        val start = withFrameMillis { it }
        while (true) {
            withFrameMillis { now -> value = ShiftLightRules.isLit(phase, frequencyHz, now - start) }
        }
    }

/**
 * Remembers the phase between readings so [ShiftLightRules] can apply its hysteresis. The same
 * reading always yields the same phase, so recomposing without a new reading changes nothing.
 */
@Composable
internal fun rememberShiftLightPhase(rpm: Double?, settings: ShiftLightSettings): ShiftLightPhase {
    val tracker = remember { PhaseTracker() }
    return tracker.update(rpm, settings)
}

private class PhaseTracker {
    private var phase = ShiftLightPhase.OFF

    fun update(rpm: Double?, settings: ShiftLightSettings): ShiftLightPhase =
        ShiftLightRules.next(phase, rpm, settings).also { phase = it }
}

private fun DrawScope.drawEdgeGlow(style: GlowStyle) {
    val depth = style.depth.toPx()
    val edge = style.color.copy(alpha = style.alpha)
    val clear = style.color.copy(alpha = 0f)
    drawRect(Brush.verticalGradient(listOf(edge, clear), 0f, depth), Offset.Zero, Size(size.width, depth))
    drawRect(
        Brush.verticalGradient(listOf(clear, edge), size.height - depth, size.height),
        Offset(0f, size.height - depth), Size(size.width, depth),
    )
    drawRect(Brush.horizontalGradient(listOf(edge, clear), 0f, depth), Offset.Zero, Size(depth, size.height))
    drawRect(
        Brush.horizontalGradient(listOf(clear, edge), size.width - depth, size.width),
        Offset(size.width - depth, 0f), Size(depth, size.height),
    )
}
