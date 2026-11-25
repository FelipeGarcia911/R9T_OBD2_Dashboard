package com.felipeg.r9t_obd2_dashboard.dashboard.ui.classic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.dashboardLabel
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftCenteredGlyph
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftColors
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftTypography
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.GearPosition
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.felipeg.r9t_obd2_dashboard.R

/** Large italic speed digits with the unit beside them, centered where the layout puts the speed. */
@Composable
internal fun ClassicSpeedReadout(speedKmh: Int?, layout: ClassicDialLayout) {
    val unit = layout.unit
    val width = unit * 1.2f
    val center = layout.speedCenter
    val speedDescription = speedKmh?.let { pluralStringResource(R.plurals.dashboard_speed_description, it, it) }
        ?: stringResource(R.string.dashboard_speed_unavailable)
    Box(
        Modifier
            .offset(x = layout.left + unit * center.x - width / 2, y = unit * (center.y - .17f))
            .width(width)
            .semantics { contentDescription = speedDescription },
        contentAlignment = Alignment.TopCenter,
    ) {
        // A spacer as wide as the unit keeps the digits centered while the unit hangs beside them.
        val side = unit * .3f
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.width(side))
            Text(
                speedKmh?.toString() ?: "–",
                color = TftColors.White,
                style = TftTypography.style(unit * .32f, FontWeight.ExtraBold),
                maxLines = 1,
            )
            Text(
                "km/h",
                color = TftColors.White,
                style = TftTypography.style(unit * .075f, FontWeight.Normal, italic = false),
                softWrap = false,
                modifier = Modifier.width(side).padding(start = unit * .025f, bottom = unit * .055f),
            )
        }
    }
}

/**
 * The gear right of the scale: a bare green N in neutral, a yellow number in a raised grey box
 * in gear.
 */
@Composable
internal fun ClassicGearIndicator(gear: GearPosition, layout: ClassicDialLayout) {
    val unit = layout.unit
    val width = unit * .3f
    val height = unit * .36f
    val center = layout.gearCenter
    val neutral = gear == GearPosition.Neutral
    val shape = RoundedCornerShape(4.dp)
    val gearDescription = if (neutral) stringResource(R.string.dashboard_gear_neutral)
    else stringResource(R.string.dashboard_gear_description, gear.dashboardLabel())
    val box = Modifier
        .offset(x = layout.left + unit * center.x - width / 2, y = unit * center.y - height / 2)
        .size(width, height)
        .semantics { contentDescription = gearDescription }
    Box(
        if (neutral) box else box
            .background(Brush.verticalGradient(listOf(TftColors.GearBoxTop, TftColors.GearBoxBottom)), shape)
            .border(1.dp, TftColors.GearBoxOutline, shape),
    ) {
        TftCenteredGlyph(
            gear.dashboardLabel(),
            if (neutral) TftColors.Green else TftColors.GearYellow,
            heightShare = if (neutral) .86f else .72f,
        )
    }
}

/** The scale's caption, beside the foot of the scale where it starts at zero. */
@Composable
internal fun ClassicScaleCaption(layout: ClassicDialLayout) {
    val unit = layout.unit
    Text(
        stringResource(R.string.dashboard_rpm_scale_stacked),
        color = TftColors.White,
        style = TftTypography.style(unit * .045f, FontWeight.Normal, italic = false),
        modifier = Modifier.offset(x = layout.left + unit * .39f, y = unit * .8f),
    )
}

/** The thin amber line with a notch at the bottom center of the instrument area. */
@Composable
internal fun BoxScope.ClassicMenuChevron(layout: ClassicDialLayout) {
    val unit = layout.unit
    Canvas(Modifier.align(Alignment.BottomCenter).size(unit * .7f, unit * .07f)) {
        val notch = size.height * .7f
        val mid = size.width / 2
        val y = size.height * .15f
        val line = Path().apply {
            moveTo(0f, y)
            lineTo(mid - notch * 1.3f, y)
            lineTo(mid, y + notch)
            lineTo(mid + notch * 1.3f, y)
            lineTo(size.width, y)
        }
        drawPath(
            line,
            Brush.horizontalGradient(listOf(TftColors.Amber.copy(alpha = 0f), TftColors.Amber, TftColors.Amber.copy(alpha = 0f))),
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
