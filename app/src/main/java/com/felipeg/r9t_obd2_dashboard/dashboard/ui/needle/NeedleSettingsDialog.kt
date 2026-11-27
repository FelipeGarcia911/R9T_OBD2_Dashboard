package com.felipeg.r9t_obd2_dashboard.dashboard.ui.needle

import androidx.compose.foundation.clickable
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import com.felipeg.r9t_obd2_dashboard.R
import com.felipeg.r9t_obd2_dashboard.dashboard.needle.NeedleSettings
import com.felipeg.r9t_obd2_dashboard.dashboard.needle.NeedleSettingsStore
import com.felipeg.r9t_obd2_dashboard.dashboard.needle.SharedPreferencesNeedleSettingsStore
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftLabelBox
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftSettingRow
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftSettingsPanel
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.tftSliderColors
import java.util.Locale
import kotlin.math.roundToInt

/** The needle settings in effect for the displays below; DashboardScreen provides the rider's. */
internal val LocalNeedleSettings = compositionLocalOf { NeedleSettings() }

@Composable
internal fun rememberNeedleSettingsStore(): NeedleSettingsStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { SharedPreferencesNeedleSettingsStore(context) }
}

/**
 * The needle's spring, tuned by hand: damping (bounce) and stiffness (speed). Every change applies
 * at once through [onChange], so the needle can be watched while tuning, ideally on the debug
 * build's real capture. Default restores the feel confirmed on the bike.
 */
@Composable
internal fun NeedleSettingsDialog(
    settings: NeedleSettings,
    onChange: (NeedleSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NeedleSettingsPanel(settings, onChange, onDismiss)
    }
}

@Composable
private fun NeedleSettingsPanel(settings: NeedleSettings, onChange: (NeedleSettings) -> Unit, onDone: () -> Unit) {
    TftSettingsPanel(
        title = stringResource(R.string.needle_title),
        onDone = onDone,
        actions = {
            TftLabelBox(
                stringResource(R.string.needle_default),
                active = false,
                modifier = Modifier.clickable(role = Role.Button, enabled = !settings.isDefault) { onChange(NeedleSettings()) },
            )
        },
    ) {
        val dampingDescription = stringResource(R.string.needle_damping)
        TftSettingRow(
            dampingDescription,
            stringResource(
                R.string.needle_damping_value,
                String.format(Locale.US, "%.2f", settings.dampingRatio),
                settings.overshootPercent.roundToInt(),
            ),
        ) {
            val steps = ((NeedleSettings.MAX_DAMPING - NeedleSettings.MIN_DAMPING) / NeedleSettings.DAMPING_STEP).roundToInt() - 1
            Slider(
                value = settings.dampingRatio,
                onValueChange = { onChange(settings.withDamping(it)) },
                valueRange = NeedleSettings.MIN_DAMPING..NeedleSettings.MAX_DAMPING,
                steps = steps,
                colors = tftSliderColors(),
                modifier = Modifier.semantics { contentDescription = dampingDescription },
            )
        }
        val stiffnessDescription = stringResource(R.string.needle_stiffness)
        TftSettingRow(
            stiffnessDescription,
            stringResource(R.string.needle_stiffness_value, settings.stiffness.roundToInt(), settings.settleMillis),
        ) {
            Slider(
                value = settings.stiffnessPosition,
                onValueChange = { onChange(settings.withStiffnessPosition(it)) },
                colors = tftSliderColors(),
                modifier = Modifier.semantics { contentDescription = stiffnessDescription },
            )
        }
    }
}

@Preview(name = "Needle settings", widthDp = 520, heightDp = 360)
@Composable
private fun NeedleSettingsPreview() = NeedleSettingsPanel(NeedleSettings(), {}, {})
