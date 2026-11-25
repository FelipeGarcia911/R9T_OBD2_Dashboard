package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.felipeg.r9t_obd2_dashboard.R

/**
 * Displays the rider can pick from the selector, in tab order. To add a display, add an entry
 * here and its panel in DashboardScreen.
 */
internal enum class DashboardLayout(@StringRes val title: Int) {
    SPORT(R.string.dashboard_display_sport),
    CLASSIC(R.string.dashboard_display_classic);

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
