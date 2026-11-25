package com.felipeg.r9t_obd2_dashboard.dashboard.shiftlight

import android.content.Context
import androidx.core.content.edit

/** Keeps the rider's shift light settings between sessions. */
internal interface ShiftLightSettingsStore {
    fun load(): ShiftLightSettings
    fun save(settings: ShiftLightSettings)
}

internal class SharedPreferencesShiftLightSettingsStore(context: Context) : ShiftLightSettingsStore {
    private val preferences = context.getSharedPreferences("shift_light", Context.MODE_PRIVATE)

    /** Falls back to the defaults when nothing is stored or the stored values are no longer valid. */
    override fun load(): ShiftLightSettings {
        val defaults = ShiftLightSettings()
        return runCatching {
            ShiftLightSettings(
                enabled = preferences.getBoolean(ENABLED, defaults.enabled),
                startRpm = preferences.getInt(START_RPM, defaults.startRpm),
                shiftRpm = preferences.getInt(SHIFT_RPM, defaults.shiftRpm),
                frequencyHz = preferences.getInt(FREQUENCY_HZ, defaults.frequencyHz),
            )
        }.getOrDefault(defaults)
    }

    override fun save(settings: ShiftLightSettings) {
        preferences.edit {
            putBoolean(ENABLED, settings.enabled)
            putInt(START_RPM, settings.startRpm)
            putInt(SHIFT_RPM, settings.shiftRpm)
            putInt(FREQUENCY_HZ, settings.frequencyHz)
        }
    }

    private companion object {
        const val ENABLED = "enabled"
        const val START_RPM = "start_rpm"
        const val SHIFT_RPM = "shift_rpm"
        const val FREQUENCY_HZ = "frequency_hz"
    }
}
