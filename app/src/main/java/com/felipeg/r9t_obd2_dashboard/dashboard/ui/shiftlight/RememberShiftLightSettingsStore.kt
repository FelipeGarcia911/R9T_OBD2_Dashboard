package com.felipeg.r9t_obd2_dashboard.dashboard.ui.shiftlight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.felipeg.r9t_obd2_dashboard.dashboard.shiftlight.SharedPreferencesShiftLightSettingsStore
import com.felipeg.r9t_obd2_dashboard.dashboard.shiftlight.ShiftLightSettingsStore

@Composable
internal fun rememberShiftLightSettingsStore(): ShiftLightSettingsStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { SharedPreferencesShiftLightSettingsStore(context) }
}
