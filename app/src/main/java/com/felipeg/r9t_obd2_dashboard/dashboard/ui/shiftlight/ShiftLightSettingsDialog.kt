package com.felipeg.r9t_obd2_dashboard.dashboard.ui.shiftlight

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.felipeg.r9t_obd2_dashboard.dashboard.shiftlight.ShiftLightPhase
import com.felipeg.r9t_obd2_dashboard.dashboard.shiftlight.ShiftLightSettings
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftColors
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftLabelBox
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftTypography
import java.text.NumberFormat
import kotlin.math.roundToInt

private val PanelShape = RoundedCornerShape(6.dp)

/**
 * The shift light configuration, as the TFT's Settings › Vehicle settings › Shift light page:
 * on or off, start RPM, shift RPM and flashing frequency. Every change applies at once through
 * [onChange]; a lamp beside the frequency flashes at the chosen rate.
 */
@Composable
internal fun ShiftLightSettingsDialog(
    settings: ShiftLightSettings,
    onChange: (ShiftLightSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        ShiftLightSettingsPanel(settings, onChange, onDismiss)
    }
}

@Composable
private fun ShiftLightSettingsPanel(
    settings: ShiftLightSettings,
    onChange: (ShiftLightSettings) -> Unit,
    onDone: () -> Unit,
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
                "SHIFT LIGHT",
                color = TftColors.Amber,
                style = TftTypography.style(18.dp, FontWeight.Medium, italic = false),
                modifier = Modifier.weight(1f),
            )
            TftLabelBox(
                if (settings.enabled) "On" else "Off",
                active = settings.enabled,
                modifier = Modifier
                    .clickable(role = Role.Switch) { onChange(settings.copy(enabled = !settings.enabled)) }
                    .semantics { contentDescription = "Shift light ${if (settings.enabled) "on" else "off"}" },
            )
        }
        val enabled = settings.enabled
        RpmSetting("Start RPM", settings.startRpm, enabled) { onChange(settings.withStart(it)) }
        RpmSetting("Shift RPM", settings.shiftRpm, enabled) { onChange(settings.withShift(it)) }
        FrequencySetting(settings.frequencyHz, enabled) { onChange(settings.withFrequency(it)) }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TftLabelBox("Done", active = true, modifier = Modifier.clickable(role = Role.Button, onClick = onDone))
        }
    }
}

@Composable
private fun RpmSetting(label: String, rpm: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val range = ShiftLightSettings.RPM_RANGE
    SettingRow(label, "${NumberFormat.getIntegerInstance().format(rpm)} rpm", enabled) {
        Slider(
            value = rpm.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / ShiftLightSettings.RPM_STEP - 1,
            enabled = enabled,
            colors = settingSliderColors(),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun FrequencySetting(hz: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val range = ShiftLightSettings.FREQUENCY_RANGE
    SettingRow("Frequency", if (hz == 0) "Steady" else "$hz Hz", enabled, lamp = { if (enabled) PreviewLamp(hz) }) {
        Slider(
            value = hz.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            enabled = enabled,
            colors = settingSliderColors(),
            modifier = Modifier.semantics { contentDescription = "Flashing frequency" },
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    enabled: Boolean,
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

/** A small lamp flashing at the chosen frequency, as BMW's light demonstrates a new setting. */
@Composable
private fun PreviewLamp(hz: Int) {
    val lit by rememberShiftLightLit(ShiftLightPhase.SHIFT, hz)
    Box(
        Modifier.size(14.dp)
            .background(if (lit) TftColors.Red else TftColors.RedTrack, CircleShape)
            .semantics { contentDescription = "Frequency preview" },
    )
}

@Composable
private fun settingSliderColors() = SliderDefaults.colors(
    thumbColor = TftColors.Amber,
    activeTrackColor = TftColors.Amber,
    inactiveTrackColor = TftColors.BarDim,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
    disabledThumbColor = TftColors.TickDim,
    disabledActiveTrackColor = TftColors.BarDim,
    disabledInactiveTrackColor = TftColors.SegmentDim,
)

@Preview(name = "Shift light settings", widthDp = 520, heightDp = 400)
@Composable
private fun ShiftLightSettingsPreview() = ShiftLightSettingsPanel(ShiftLightSettings(), {}, {})
