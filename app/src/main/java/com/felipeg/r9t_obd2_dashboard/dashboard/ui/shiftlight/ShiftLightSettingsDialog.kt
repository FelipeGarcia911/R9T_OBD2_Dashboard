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
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftSettingRow
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftSettingsPanel
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.tftSliderColors
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftTypography
import java.text.NumberFormat
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.felipeg.r9t_obd2_dashboard.R

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
    val switchDescription = stringResource(
        if (settings.enabled) R.string.shift_light_switch_on else R.string.shift_light_switch_off,
    )
    TftSettingsPanel(
        title = stringResource(R.string.shift_light_title),
        onDone = onDone,
        titleEnd = {
            TftLabelBox(
                stringResource(if (settings.enabled) R.string.shift_light_on else R.string.shift_light_off),
                active = settings.enabled,
                modifier = Modifier
                    .clickable(role = Role.Switch) { onChange(settings.copy(enabled = !settings.enabled)) }
                    .semantics { contentDescription = switchDescription },
            )
        },
    ) {
        val enabled = settings.enabled
        RpmSetting(stringResource(R.string.shift_light_start_rpm), settings.startRpm, enabled) { onChange(settings.withStart(it)) }
        RpmSetting(stringResource(R.string.shift_light_shift_rpm), settings.shiftRpm, enabled) { onChange(settings.withShift(it)) }
        FrequencySetting(settings.frequencyHz, enabled) { onChange(settings.withFrequency(it)) }
    }
}

@Composable
private fun RpmSetting(label: String, rpm: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val range = ShiftLightSettings.RPM_RANGE
    TftSettingRow(label, "${NumberFormat.getIntegerInstance().format(rpm)} rpm", enabled) {
        Slider(
            value = rpm.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / ShiftLightSettings.RPM_STEP - 1,
            enabled = enabled,
            colors = tftSliderColors(),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun FrequencySetting(hz: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val range = ShiftLightSettings.FREQUENCY_RANGE
    val sliderDescription = stringResource(R.string.shift_light_frequency_slider)
    TftSettingRow(
        stringResource(R.string.shift_light_frequency),
        if (hz == 0) stringResource(R.string.shift_light_steady) else "$hz Hz",
        enabled,
        lamp = { if (enabled) PreviewLamp(hz) },
    ) {
        Slider(
            value = hz.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            enabled = enabled,
            colors = tftSliderColors(),
            modifier = Modifier.semantics { contentDescription = sliderDescription },
        )
    }
}

/** A small lamp flashing at the chosen frequency, as BMW's light demonstrates a new setting. */
@Composable
private fun PreviewLamp(hz: Int) {
    val lit by rememberShiftLightLit(ShiftLightPhase.SHIFT, hz)
    val description = stringResource(R.string.shift_light_frequency_preview)
    Box(
        Modifier.size(14.dp)
            .background(if (lit) TftColors.Red else TftColors.RedTrack, CircleShape)
            .semantics { contentDescription = description },
    )
}

@Preview(name = "Shift light settings", widthDp = 520, heightDp = 400)
@Composable
private fun ShiftLightSettingsPreview() = ShiftLightSettingsPanel(ShiftLightSettings(), {}, {})
