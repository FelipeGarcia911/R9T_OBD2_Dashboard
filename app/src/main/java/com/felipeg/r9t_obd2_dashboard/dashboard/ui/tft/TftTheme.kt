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

/**
 * TFT palette: near-black ground with a slightly lighter top bar, white scale and readouts,
 * teal-cyan accents for the mode, rule and box frames, red limit zone.
 */
internal object TftColors {
    val GroundCenter = Color(0xFF0B0D0F)
    val GroundEdge = Color(0xFF000000)
    val TopBarTop = Color(0xFF23292D)
    val TopBarBottom = Color(0xFF15191C)
    val White = Color(0xFFF3F6F8)
    val TickDim = Color(0xFF8C99A3)
    val Cyan = Color(0xFF2EC1E0)
    val Red = Color(0xFFF0141F)
    val RedTrack = Color(0xFF4A0E13)
    val Green = Color(0xFF55D63A)
    val Label = Color(0xFFA7B3BB)
    val GearBoxTop = Color(0xFF3A464F)
    val GearBoxBottom = Color(0xFF1B232A)
    val GearBoxOutline = Color(0xFF55636D)
    val ControlOutline = Color(0xFF3A4650)
    val ControlFill = Color(0x1F2EC1E0)
    val BarLit = Color(0xFFC9D3D9)
    val BarDim = Color(0xFF26313A)
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
        upright(FontWeight.SemiBold),
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
