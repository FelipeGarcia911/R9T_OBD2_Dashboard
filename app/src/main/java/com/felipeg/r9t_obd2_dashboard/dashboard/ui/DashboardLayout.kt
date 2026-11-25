package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Displays the rider can pick from the selector, in tab order. To add a display, add an entry
 * here and its panel in DashboardScreen.
 */
internal enum class DashboardLayout(val title: String) {
    SPORT("Sport"),
    CLASSIC("Classic");

    companion object {
        val DEFAULT = SPORT
    }
}

/** Remembers the rider's layout choice between sessions. */
internal interface DashboardLayoutStore {
    fun load(): DashboardLayout
    fun save(layout: DashboardLayout)
}

internal class SharedPreferencesDashboardLayoutStore(context: Context) : DashboardLayoutStore {
    private val preferences = context.getSharedPreferences("dashboard", Context.MODE_PRIVATE)

    override fun load(): DashboardLayout =
        DashboardLayout.entries.firstOrNull { it.name == preferences.getString(KEY, null) }
            ?: DashboardLayout.DEFAULT

    override fun save(layout: DashboardLayout) {
        preferences.edit().putString(KEY, layout.name).apply()
    }

    private companion object {
        const val KEY = "layout"
    }
}

@Composable
internal fun rememberDashboardLayoutStore(): DashboardLayoutStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { SharedPreferencesDashboardLayoutStore(context) }
}
