package com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.felipeg.r9t_obd2_dashboard.R

private val PanelShape = RoundedCornerShape(6.dp)

/**
 * A TFT settings page: an amber title with an optional control at its end, the settings, and a
 * row of buttons ending in Done. Shared by every dashboard settings dialog.
 */
@Composable
internal fun TftSettingsPanel(
    title: String,
    onDone: () -> Unit,
    titleEnd: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.widthIn(max = 520.dp)
            .background(TftColors.GroundCenter, PanelShape)
            .border(1.dp, TftColors.ControlOutline, PanelShape)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(),
                color = TftColors.Amber,
                style = TftTypography.style(18.dp, FontWeight.Medium, italic = false),
                modifier = Modifier.weight(1f),
            )
            titleEnd()
        }
        content()
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions()
            TftLabelBox(stringResource(R.string.done), active = true, modifier = Modifier.clickable(role = Role.Button, onClick = onDone))
        }
    }
}

/** One setting: its name and current value on a line, an optional [lamp], and the control below. */
@Composable
internal fun TftSettingRow(
    label: String,
    value: String,
    enabled: Boolean = true,
    lamp: @Composable () -> Unit = {},
    control: @Composable () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                label.uppercase(),
                color = if (enabled) TftColors.White else TftColors.TickDim,
                style = TftTypography.style(13.dp, FontWeight.SemiBold, italic = false),
                modifier = Modifier.weight(1f),
            )
            lamp()
            Text(
                value,
                color = if (enabled) TftColors.White else TftColors.TickDim,
                style = TftTypography.style(20.dp, FontWeight.Bold),
            )
        }
        control()
    }
}

@Composable
internal fun tftSliderColors(): SliderColors = SliderDefaults.colors(
    thumbColor = TftColors.Amber,
    activeTrackColor = TftColors.Amber,
    inactiveTrackColor = TftColors.BarDim,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
    disabledThumbColor = TftColors.TickDim,
    disabledActiveTrackColor = TftColors.BarDim,
    disabledInactiveTrackColor = TftColors.SegmentDim,
)
