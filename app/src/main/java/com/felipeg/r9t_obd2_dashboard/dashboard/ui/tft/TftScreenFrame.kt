package com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Heights of the TFT's information bars. The host's toolbar fits inside the bottom bar. */
internal object TftBars {
    val TopHeight = 56.dp
    val BottomHeight = 56.dp
    val SideMargin = 20.dp
}

/**
 * The TFT screen structure: a top information bar closed by a cyan rule, the main instrument
 * area, and a bottom information bar. Each bar has start, center and end slots. The bottom
 * center slot stays empty for the host's display selector.
 */
@Composable
internal fun TftScreenFrame(
    modifier: Modifier = Modifier,
    topStart: @Composable BoxScope.() -> Unit = {},
    topCenter: @Composable BoxScope.() -> Unit = {},
    topEnd: @Composable BoxScope.() -> Unit = {},
    bottomStart: @Composable BoxScope.() -> Unit = {},
    bottomEnd: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier.fillMaxSize().background(Brush.radialGradient(listOf(TftColors.GroundCenter, TftColors.GroundEdge))),
    ) {
        InfoBar(
            Modifier.height(TftBars.TopHeight)
                .background(Brush.verticalGradient(listOf(TftColors.TopBarTop, TftColors.TopBarBottom))),
            topStart, topCenter, topEnd,
        )
        CyanRule()
        Box(Modifier.fillMaxWidth().weight(1f), content = content)
        InfoBar(Modifier.height(TftBars.BottomHeight), bottomStart, {}, bottomEnd)
    }
}

@Composable
private fun InfoBar(
    modifier: Modifier,
    start: @Composable BoxScope.() -> Unit,
    center: @Composable BoxScope.() -> Unit,
    end: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth().padding(horizontal = TftBars.SideMargin)) {
        Box(Modifier.align(Alignment.CenterStart), content = start)
        Box(Modifier.align(Alignment.Center), content = center)
        Box(Modifier.align(Alignment.CenterEnd), content = end)
    }
}

/** The thin cyan line that closes the top bar, fading at both ends. */
@Composable
private fun CyanRule() {
    Box(
        Modifier.fillMaxWidth().height(2.dp).background(
            Brush.horizontalGradient(listOf(Color.Transparent, TftColors.Cyan, TftColors.Cyan, Color.Transparent)),
        ),
    )
}
