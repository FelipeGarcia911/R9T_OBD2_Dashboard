package com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ControlShape = RoundedCornerShape(3.dp)

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

/** Visual range of a [TftBarGauge]. Bars from [warnFrom] upward turn red when lit. */
internal data class TftGaugeRange(val min: Double, val max: Double, val warnFrom: Double, val bars: Int = 10) {
    init {
        require(max > min && bars > 1)
    }

    /** How many bars a value lights, clamped to the gauge. Null lights none. */
    fun litBars(value: Double?): Int =
        if (value == null) 0 else (((value - min) / (max - min)) * bars).toInt().coerceIn(0, bars)

    /** Bar indices from the bottom, 0 based, that sit in the warning zone. */
    fun isWarning(bar: Int): Boolean = min + (max - min) * (bar + 1) / bars > warnFrom
}

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
            if (scaleOnStart) GaugeScale(range, TextAlign.End)
            Bars(range, range.litBars(value), Modifier.weight(1f).fillMaxHeight().padding(horizontal = 6.dp))
            if (!scaleOnStart) GaugeScale(range, TextAlign.Start)
        }
        Text(
            valueText,
            color = if (value == null) TftColors.TickDim else TftColors.White,
            style = TftTypography.style(20.dp, FontWeight.Bold),
        )
    }
}

@Composable
private fun Bars(range: TftGaugeRange, lit: Int, modifier: Modifier) {
    Canvas(modifier) {
        val gap = 3.dp.toPx()
        val barHeight = (size.height - gap * (range.bars - 1)) / range.bars
        for (bar in 0 until range.bars) {
            val top = size.height - (bar + 1) * barHeight - bar * gap
            val warning = range.isWarning(bar)
            val color = when {
                bar < lit && warning -> TftColors.Red
                bar < lit -> TftColors.BarLit
                warning -> TftColors.RedTrack
                else -> TftColors.BarDim
            }
            drawRect(color, Offset(0f, top), Size(size.width, barHeight))
        }
    }
}

@Composable
private fun GaugeScale(range: TftGaugeRange, align: TextAlign) {
    val style = TftTypography.style(11.dp, FontWeight.Medium, italic = false)
    Column(
        Modifier.width(30.dp).fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        listOf(range.max, (range.min + range.max) / 2, range.min).forEach { mark ->
            Text(
                mark.toInt().toString(),
                color = TftColors.Label,
                style = style,
                textAlign = align,
                modifier = Modifier.fillMaxWidth(),
            )
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
