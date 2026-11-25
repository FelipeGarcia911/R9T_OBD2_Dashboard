package com.felipeg.r9t_obd2_dashboard.dashboard.ui.sport

import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftCenteredGlyph
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftColors
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftTypography
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.dashboardLabel
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.GearPosition

/** Speed in the middle of the dial: large italic digits, unit, cyan rule, and scale caption. */
@Composable
internal fun SportSpeedReadout(speedKmh: Int?, layout: SportDialLayout) {
    val radius = layout.radius
    val width = radius * 1.1f
    Column(
        Modifier
            .offset(x = layout.centerX - width / 2, y = layout.centerY - radius * .62f)
            .width(width)
            .semantics { contentDescription = speedKmh?.let { "$it kilometers per hour" } ?: "Speed unavailable" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                speedKmh?.toString() ?: "–",
                color = TftColors.White,
                style = TftTypography.style(radius * .40f, FontWeight.ExtraBold),
                maxLines = 1,
            )
            Text(
                "km/h",
                color = TftColors.White,
                style = TftTypography.style(radius * .085f, FontWeight.Medium, italic = false),
                modifier = Modifier.padding(start = radius * .03f, bottom = radius * .06f),
            )
        }
        Box(Modifier.width(radius * .62f).height(2.dp).background(TftColors.Cyan))
        Text(
            "RPM x 1000",
            color = TftColors.Label,
            style = TftTypography.style(radius * .06f, FontWeight.Normal, italic = false),
            modifier = Modifier.padding(top = radius * .025f),
        )
    }
}

/** Gear in a raised box: green N for neutral, cyan for a gear number. The caller sizes it. */
@Composable
internal fun SportGearBox(gear: GearPosition, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    val label = gear.dashboardLabel()
    val color = if (gear == GearPosition.Neutral) TftColors.Green else TftColors.Cyan
    Box(
        modifier
            .background(Brush.verticalGradient(listOf(TftColors.GearBoxTop, TftColors.GearBoxBottom)), shape)
            .border(1.dp, TftColors.GearBoxOutline, shape)
            .semantics { contentDescription = if (gear == GearPosition.Neutral) "Neutral" else "Gear $label" },
    ) {
        TftCenteredGlyph(label, color)
    }
}
