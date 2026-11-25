package com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.core.content.res.ResourcesCompat
import com.felipeg.r9t_obd2_dashboard.R

/** TFT palette: blue-black ground, white scale, cyan accents, red limit zone. */
internal object TftColors {
    val GroundCenter = Color(0xFF111A22)
    val GroundEdge = Color(0xFF03060A)
    val White = Color(0xFFF3F6F8)
    val TickDim = Color(0xFF8C99A3)
    val Track = Color(0xFF1F2931)
    val Cyan = Color(0xFF35BDEF)
    val Red = Color(0xFFF0141F)
    val RedTrack = Color(0xFF4A0E13)
    val Green = Color(0xFF55D63A)
    val Label = Color(0xFFA7B3BB)
    val GearBoxTop = Color(0xFF3A464F)
    val GearBoxBottom = Color(0xFF1B232A)
    val GearBoxOutline = Color(0xFF55636D)
}

/**
 * Saira (SIL Open Font License, see assets/licenses/Saira-OFL.txt) has the rounded, squared
 * italic numerals of a sport TFT. Both files are variable fonts; each weight sets its axis.
 */
internal object TftTypography {
    private const val NUMERAL_WEIGHT = 700

    val family = FontFamily(
        italic(FontWeight.Black),
        italic(FontWeight.ExtraBold),
        italic(FontWeight.Bold),
        upright(FontWeight.Medium),
        upright(FontWeight.Normal),
    )

    /** Text sized from the dial, so the layout scales with the screen rather than the font setting. */
    @Composable
    fun style(size: Dp, weight: FontWeight = FontWeight.Black, italic: Boolean = true): TextStyle {
        val fontSize = with(LocalDensity.current) { size.toSp() / fontScale }
        return TextStyle(
            fontFamily = family,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = weight,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        )
    }

    /**
     * Paint for numerals drawn directly on a canvas, such as the tachometer scale.
     * Left-aligned, so callers position text from its measured bounds or baseline.
     */
    @Composable
    fun rememberNumeralPaint(weight: Int = NUMERAL_WEIGHT): Paint {
        val context = LocalContext.current
        return remember(context, weight) { numeralPaint(context, weight) }
    }

    private fun numeralPaint(context: Context, weight: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.saira_italic)
            ?: Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
        fontVariationSettings = "'wght' $weight"
    }

    @OptIn(ExperimentalTextApi::class)
    private fun italic(weight: FontWeight) = Font(
        R.font.saira_italic, weight, FontStyle.Italic,
        variationSettings = FontVariation.Settings(weight, FontStyle.Italic),
    )

    @OptIn(ExperimentalTextApi::class)
    private fun upright(weight: FontWeight) = Font(
        R.font.saira, weight, FontStyle.Normal,
        variationSettings = FontVariation.Settings(weight, FontStyle.Normal),
    )
}
