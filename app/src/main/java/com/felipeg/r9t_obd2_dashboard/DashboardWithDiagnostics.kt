package com.felipeg.r9t_obd2_dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.DashboardMenuItem
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.DashboardScreen
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.link.DashboardConnection
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState

/**
 * The dashboard for [session], the app's main screen. Diagnostics in its menu, after
 * [menuItems], opens [diagnostics], which returns through `backToDashboard` or Back without
 * stopping the session.
 */
@Composable
internal fun DashboardWithDiagnostics(
    session: DiagnosticState,
    sourceLabel: String,
    connection: DashboardConnection,
    modifier: Modifier,
    menuItems: List<DashboardMenuItem> = emptyList(),
    diagnostics: @Composable (backToDashboard: () -> Unit) -> Unit,
) {
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    if (showDiagnostics) {
        BackHandler { showDiagnostics = false }
        diagnostics { showDiagnostics = false }
    } else {
        DashboardScreen(
            state = session,
            modifier = modifier,
            sourceLabel = sourceLabel,
            connection = connection,
            menuItems = menuItems + DashboardMenuItem(stringResource(R.string.dashboard_menu_diagnostics)) {
                showDiagnostics = true
            },
        )
    }
}

/**
 * Calls [connect] once when the screen first opens with [session] stopped, so the dashboard comes
 * up connecting on its own. Not again on rotation, nor after the rider disconnects.
 */
@Composable
internal fun ConnectOnOpen(session: DiagnosticState, connect: () -> Unit) {
    var done by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (done) return@LaunchedEffect
        done = true
        if (session is DiagnosticState.Disconnected || session is DiagnosticState.Failed) connect()
    }
}
