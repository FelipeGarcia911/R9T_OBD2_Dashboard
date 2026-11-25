package com.felipeg.r9t_obd2_dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.DashboardScreen
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState

/**
 * Shows [diagnostics] until it calls `openDashboard`, then the dashboard for [session]. Back, or
 * Diagnostics in the dashboard menu, returns without stopping the session.
 */
@Composable
internal fun DiagnosticsOrDashboard(
    session: DiagnosticState,
    sourceLabel: String,
    modifier: Modifier,
    diagnostics: @Composable (openDashboard: () -> Unit) -> Unit,
) {
    var showDashboard by rememberSaveable { mutableStateOf(false) }
    if (showDashboard) {
        BackHandler { showDashboard = false }
        DashboardScreen(
            state = session,
            onDiagnostics = { showDashboard = false },
            modifier = modifier,
            sourceLabel = sourceLabel,
        )
    } else {
        diagnostics { showDashboard = true }
    }
}
