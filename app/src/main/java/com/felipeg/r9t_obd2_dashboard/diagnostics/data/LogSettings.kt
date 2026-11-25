package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import android.content.Context

/** Whether the next connection writes a detailed ELM327 session log. */
internal interface LogSettings {
    var detailedLogging: Boolean
}

internal class SharedPreferencesLogSettings(context: Context) : LogSettings {
    private val preferences = context.getSharedPreferences("elm-diagnostics", Context.MODE_PRIVATE)

    override var detailedLogging: Boolean
        get() = preferences.getBoolean(DETAILED_LOGGING, true)
        set(enabled) {
            preferences.edit().putBoolean(DETAILED_LOGGING, enabled).apply()
        }

    private companion object {
        const val DETAILED_LOGGING = "detailed-session-logging"
    }
}
