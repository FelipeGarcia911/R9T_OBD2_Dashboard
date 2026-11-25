package com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The mode slot of the top bar: here the connection status, in the view's accent color. */
@Composable
internal fun TftModeText(text: String, color: Color) = Text(
    text.uppercase(),
    color = color,
    style = TftTypography.style(18.dp, FontWeight.Medium, italic = false),
    maxLines = 1,
)

@Composable
internal fun TftClockText(time: String) = Text(
    time,
    color = TftColors.White,
    style = TftTypography.style(22.dp, FontWeight.Medium, italic = false),
)

private const val GLYPH_WEIGHT = 900

/**
 * One heavy italic numeral or letter, such as the gear, sized to [heightShare] of the space it is
 * given and centered on its ink.
 */
@Composable
internal fun TftCenteredGlyph(text: String, color: Color, modifier: Modifier = Modifier, heightShare: Float = .74f) {
    val paint = TftTypography.rememberNumeralPaint(weight = GLYPH_WEIGHT)
    Canvas(modifier.fillMaxSize()) {
        paint.textSize = size.height * heightShare
        paint.color = color.toArgb()
        drawTextCenteredOnGlyph(text, paint)
    }
}

/**
 * Centers the glyph's ink, not its text box. Font ascent, descent and the italic slant would
 * otherwise push the character low and to one side.
 */
private fun DrawScope.drawTextCenteredOnGlyph(text: String, paint: Paint) {
    val ink = Rect()
    paint.getTextBounds(text, 0, text.length, ink)
    val x = size.width / 2 - ink.exactCenterX()
    val y = size.height / 2 - ink.exactCenterY()
    drawIntoCanvas { it.nativeCanvas.drawText(text, x, y, paint) }
}
