package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.classic.ClassicDashboardPanel
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.sport.SportDashboardPanel
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.rememberDashboardClock

@Composable
internal fun DashboardScreen(
    state: DiagnosticState,
    onDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    sourceLabel: String = "Simulation",
    layoutStore: DashboardLayoutStore = rememberDashboardLayoutStore(),
) {
    RideMode()
    val time = rememberDashboardClock()
    val status = state.dashboardStatus()
    val live = state as? DiagnosticState.Live
    var layout by rememberSaveable { mutableStateOf(layoutStore.load()) }
    Box(modifier.fillMaxSize()) {
        when (layout) {
            DashboardLayout.SPORT -> SportDashboardPanel(live, time, status)
            DashboardLayout.CLASSIC -> ClassicDashboardPanel(live, time, status)
        }
        // Same place on every display, so the rider always finds it in one spot.
        DashboardToolbar(
            selected = layout,
            onSelect = { selected ->
                layout = selected
                layoutStore.save(selected)
            },
            sourceLabel = sourceLabel,
            onDiagnostics = onDiagnostics,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = TOOLBAR_BOTTOM_INSET),
        )
    }
}

/** Distance from the bottom edge to the shared toolbar. Displays keep their bottom center free. */
internal val TOOLBAR_BOTTOM_INSET = 14.dp

/** Short connection status for the dashboard footer; null while readings are live. */
private fun DiagnosticState.dashboardStatus(): String? = when (this) {
    DiagnosticState.Disconnected -> "Disconnected"
    DiagnosticState.ConnectingBluetooth -> "Connecting"
    DiagnosticState.WaitingForAdapter -> "Waiting for adapter"
    DiagnosticState.WaitingForEcu -> "Waiting for ECU"
    is DiagnosticState.Reconnecting -> "Link lost · Reconnecting ($attempt)…"
    is DiagnosticState.Failed -> "Session failed"
    is DiagnosticState.Live -> null
}
