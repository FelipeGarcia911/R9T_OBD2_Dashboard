package com.felipeg.r9t_obd2_dashboard

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.DashboardScreen
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.AdapterConnectDialogs
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.DiagnosticScreen
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.ElmLogControls
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.rememberAdapterConnectFlow
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.rememberDiagnosticViewModel

/** Live diagnostics over a paired ELM327, and the dashboard fed by the same session. */
@Composable
internal fun LiveDiagnosticContent(modifier: Modifier, onShowDemo: (() -> Unit)? = null) {
    val activity = LocalContext.current as ComponentActivity
    val container = appContainer()
    val model = rememberDiagnosticViewModel(activity, "live-diagnostics", container::liveSession)
    val state by model.uiState.collectAsStateWithLifecycle()
    val trace by model.trace.collectAsStateWithLifecycle()
    val connectFlow = rememberAdapterConnectFlow(activity, container.pairedAdapters, model::connect)
    var showDashboard by rememberSaveable { mutableStateOf(false) }

    if (showDashboard) {
        BackHandler { showDashboard = false }
        DashboardScreen(
            state = state.session,
            onDiagnostics = { showDashboard = false },
            modifier = modifier,
            sourceLabel = "Live ELM327",
        )
        return
    }
    Column(modifier) {
        ElmLogControls(container.logSettings, container.sessionLogs)
        if (onShowDemo != null) {
            OutlinedButton(onClick = {
                model.disconnect()
                onShowDemo()
            }) { Text("Show simulation") }
        }
        OutlinedButton(
            enabled = state.session is DiagnosticState.Live,
            onClick = { showDashboard = true },
        ) { Text("Open dashboard") }
        DiagnosticScreen(
            state = state,
            trace = trace,
            onConnect = connectFlow::connect,
            onDisconnect = model::disconnect,
        )
    }
    AdapterConnectDialogs(connectFlow)
}
