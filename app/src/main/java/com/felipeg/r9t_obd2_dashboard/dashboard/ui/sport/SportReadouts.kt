package com.felipeg.r9t_obd2_dashboard.dashboard.ui.sport

import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftColors
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftTypography
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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

/** Gear in a raised box: green N for neutral, cyan for a gear number. */
@Composable
internal fun SportGearBox(gear: GearPosition, radius: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    val label = gear.dashboardLabel()
    val color = if (gear == GearPosition.Neutral) TftColors.Green else TftColors.Cyan
    Box(
        modifier
            .size(radius * .30f, radius * .34f)
            .background(Brush.verticalGradient(listOf(TftColors.GearBoxTop, TftColors.GearBoxBottom)), shape)
            .border(1.dp, TftColors.GearBoxOutline, shape)
            .semantics { contentDescription = if (gear == GearPosition.Neutral) "Neutral" else "Gear $label" },
    ) {
        val paint = TftTypography.rememberNumeralPaint(weight = GEAR_WEIGHT)
        Canvas(Modifier.fillMaxSize()) {
            paint.textSize = size.height * .74f
            paint.color = color.toArgb()
            drawTextCenteredOnGlyph(label, paint)
        }
    }
}

private const val GEAR_WEIGHT = 900

/**
 * Centers the glyph's ink, not its text box. Font ascent, descent and the italic slant would
 * otherwise push the digit low and to one side of the box.
 */
private fun DrawScope.drawTextCenteredOnGlyph(text: String, paint: Paint) {
    val ink = Rect()
    paint.getTextBounds(text, 0, text.length, ink)
    val x = size.width / 2 - ink.exactCenterX()
    val y = size.height / 2 - ink.exactCenterY()
    drawIntoCanvas { it.nativeCanvas.drawText(text, x, y, paint) }
}
