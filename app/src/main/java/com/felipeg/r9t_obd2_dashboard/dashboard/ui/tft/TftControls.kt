package com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner of every TFT control box, so boxes drawn elsewhere match the label boxes. */
internal val ControlShape = RoundedCornerShape(3.dp)

/**
 * The TFT's outlined label box, as used for DTC and BRAKE. Active boxes have a cyan frame
 * and white text; inactive ones fade to grey.
 */
@Composable
internal fun TftLabelBox(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    textSize: Dp = 14.dp,
) {
    Box(
        modifier
            .background(if (active) TftColors.ControlFill else TftColors.GroundEdge.copy(alpha = .6f), ControlShape)
            .border(if (active) 1.5.dp else 1.dp, if (active) TftColors.Cyan else TftColors.ControlOutline, ControlShape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            color = if (active) TftColors.White else TftColors.TickDim,
            style = TftTypography.style(textSize, FontWeight.Medium, italic = false),
            maxLines = 1,
        )
    }
}

/**
 * Visual range of a [TftBarGauge]. Bars from [warnFrom] upward turn red when lit. Counting rows
 * from the top, every [majorEvery]th row is a major row with a long marker and a scale label;
 * the bottom row is major too and carries the unit, as on the original bar graphs.
 */
internal data class TftGaugeRange(
    val min: Double,
    val max: Double,
    val warnFrom: Double,
    val bars: Int = 10,
    val majorEvery: Int = 2,
) {
    init {
        require(max > min && bars > 1 && majorEvery > 0)
    }

    /** How many bars a value lights, clamped to the gauge. Null lights none. */
    fun litBars(value: Double?): Int =
        if (value == null) 0 else (((value - min) / (max - min)) * bars).toInt().coerceIn(0, bars)

    /** Bar indices from the bottom, 0 based, that sit in the warning zone. */
    fun isWarning(bar: Int): Boolean = min + (max - min) * (bar + 1) / bars > warnFrom

    /** Whether [bar], counted from the bottom, gets a long marker. */
    fun isMajor(bar: Int): Boolean = bar == 0 || (bars - 1 - bar) % majorEvery == 0

    /**
     * The scale label beside [bar]: the value at the top of a major row, [unit] on the bottom
     * row, nothing beside minor rows.
     */
    fun scaleLabel(bar: Int, unit: String): String? = when {
        bar == 0 -> unit
        isMajor(bar) -> (min + (max - min) * (bar + 1) / bars).roundToInt().toString()
        else -> null
    }
}

/**
 * The stepped outline of the TFT bar graph. Each row has a marker next to the scale and a long
 * bar whose free end steps inward from the top row to the bottom row. Markers of major rows
 * reach further toward the scale; all markers line up on the long bar's side.
 */
internal object TftStepProfile {
    /** The bottom row's long bar, as a share of the top row's. */
    const val SHORTEST = .62f

    /** Share of the gauge width taken by the marker column, which a major marker fills. */
    const val MARKER_SHARE = .2f

    /** A minor row's marker, as a share of a major row's. */
    const val MINOR_MARKER = .6f

    /** Length of [bar]'s long bar, from 0 (bottom) to [bars] - 1 (top), as a share of the longest. */
    fun longBarShare(bar: Int, bars: Int): Float = SHORTEST + (1 - SHORTEST) * bar / (bars - 1)

    /** Top of [bar], counted from the bottom, in a column of [height] with [gap] between rows. */
    fun rowTop(bar: Int, bars: Int, height: Float, gap: Float): Float =
        height - (bar + 1) * rowHeight(bars, height, gap) - bar * gap

    fun rowHeight(bars: Int, height: Float, gap: Float): Float = (height - gap * (bars - 1)) / bars
}

private val RowGap = 2.dp

/**
 * The TFT's stacked-bar indicator: a label box, horizontal bars lit from the bottom, scale
 * marks on the outer side, and the current value underneath. Set [scaleOnStart] for the left
 * side of the screen so the marks face outward, as on the original.
 */
@Composable
internal fun TftBarGauge(
    label: String,
    value: Double?,
    valueText: String,
    range: TftGaugeRange,
    scaleOnStart: Boolean,
    unit: String,
    modifier: Modifier = Modifier,
    barsHeight: Dp = 96.dp,
) {
    Column(
        modifier.semantics { contentDescription = "$label $valueText" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TftLabelBox(label, active = value != null, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().height(barsHeight), verticalAlignment = Alignment.CenterVertically) {
            if (scaleOnStart) GaugeScale(range, unit, TextAlign.End)
            SteppedBars(
                range, range.litBars(value), anchorAtStart = scaleOnStart,
                modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 6.dp),
            )
            if (!scaleOnStart) GaugeScale(range, unit, TextAlign.Start)
        }
        Text(
            valueText,
            color = if (value == null) TftColors.TickDim else TftColors.White,
            style = TftTypography.style(20.dp, FontWeight.Bold),
        )
    }
}

/**
 * Rows lit from the bottom, anchored on the scale side ([anchorAtStart] when the scale is on the
 * left) so the stepped edge faces the dial, mirrored on each side as on the original.
 */
@Composable
private fun SteppedBars(range: TftGaugeRange, lit: Int, anchorAtStart: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val rowGap = RowGap.toPx()
        val columnGap = 4.dp.toPx()
        val rowHeight = TftStepProfile.rowHeight(range.bars, size.height, rowGap)
        val column = size.width * TftStepProfile.MARKER_SHARE
        val longest = size.width - column - columnGap
        for (bar in 0 until range.bars) {
            val top = TftStepProfile.rowTop(bar, range.bars, size.height, rowGap)
            val color = barColor(lit = bar < lit, warning = range.isWarning(bar))
            val long = longest * TftStepProfile.longBarShare(bar, range.bars)
            val marker = column * if (range.isMajor(bar)) 1f else TftStepProfile.MINOR_MARKER
            // Lay the row out from the anchor: marker column, gap, then the long bar. Markers
            // line up on the long bar's side, so a major one reaches further toward the scale.
            val markerX = if (anchorAtStart) column - marker else size.width - column
            val longX = if (anchorAtStart) column + columnGap else size.width - column - columnGap - long
            drawRect(color, Offset(markerX, top), Size(marker, rowHeight))
            drawRect(color, Offset(longX, top), Size(long, rowHeight))
        }
    }
}

private fun barColor(lit: Boolean, warning: Boolean): Color = when {
    lit && warning -> TftColors.Red
    lit -> TftColors.BarLit
    warning -> TftColors.RedTrack
    else -> TftColors.BarDim
}

/** Labels centered on the rows they name, so each sits level with its long marker. */
@Composable
private fun GaugeScale(range: TftGaugeRange, unit: String, align: TextAlign) {
    val style = TftTypography.style(11.dp, FontWeight.Medium, italic = false)
    BoxWithConstraints(Modifier.width(30.dp).fillMaxHeight()) {
        val height = maxHeight.value
        val rowHeight = TftStepProfile.rowHeight(range.bars, height, RowGap.value).dp
        for (bar in 0 until range.bars) {
            val label = range.scaleLabel(bar, unit) ?: continue
            Box(
                Modifier.offset(y = TftStepProfile.rowTop(bar, range.bars, height, RowGap.value).dp)
                    .height(rowHeight)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = TftColors.Label,
                    style = style,
                    textAlign = align,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(unbounded = true),
                )
            }
        }
    }
}

/** A small white caption beside its value, as the TFT labels TOTAL 5 km: BATT 12.8 V. */
@Composable
internal fun TftCaptionValue(caption: String, value: String, available: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "$caption $value" },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(caption.uppercase(), color = TftColors.White, style = TftTypography.style(12.dp, FontWeight.SemiBold, italic = false),
            modifier = Modifier.padding(bottom = 3.dp))
        Text(value, color = if (available) TftColors.White else TftColors.TickDim,
            style = TftTypography.style(22.dp, FontWeight.Bold))
    }
}
