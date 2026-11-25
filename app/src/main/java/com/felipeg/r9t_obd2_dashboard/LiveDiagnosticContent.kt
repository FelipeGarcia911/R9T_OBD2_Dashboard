package com.felipeg.r9t_obd2_dashboard

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.link.DashboardConnection
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.AdapterConnectDialogs
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.DiagnosticScreen
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.ElmLogControls
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.rememberAdapterConnectFlow
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.rememberDiagnosticViewModel
import androidx.compose.ui.res.stringResource
import com.felipeg.r9t_obd2_dashboard.R

/** Live diagnostics over a paired ELM327, and the dashboard fed by the same session. */
@Composable
internal fun LiveDiagnosticContent(modifier: Modifier, onShowDemo: (() -> Unit)? = null) {
    val activity = LocalContext.current as ComponentActivity
    val container = appContainer()
    val model = rememberDiagnosticViewModel(activity, "live-diagnostics", container::liveSession)
    val state by model.uiState.collectAsStateWithLifecycle()
    val trace by model.trace.collectAsStateWithLifecycle()
    val connectFlow = rememberAdapterConnectFlow(
        activity, container.pairedAdapters, container.lastAdapter, model::connect,
    )
    val connection = DashboardConnection(
        connect = connectFlow::reconnect,
        disconnect = model::disconnect,
        chooseAdapter = connectFlow::connect,
        adapterName = connectFlow.lastAdapter?.let { it.name ?: it.id },
    )

    DiagnosticsOrDashboard(
        state.session, stringResource(R.string.diagnostics_source_live), connection, modifier,
    ) { openDashboard ->
        Column(modifier) {
            ElmLogControls(container.logSettings, container.sessionLogs)
            if (onShowDemo != null) {
                OutlinedButton(onClick = {
                    model.disconnect()
                    onShowDemo()
                }) { Text(stringResource(R.string.show_simulation)) }
            }
            // Always available: the dashboard's connection lamp can start the session too.
            OutlinedButton(onClick = openDashboard) { Text(stringResource(R.string.open_dashboard)) }
            DiagnosticScreen(
                state = state,
                trace = trace,
                onConnect = connectFlow::connect,
                onDisconnect = model::disconnect,
            )
        }
    }
    // Over diagnostics and the dashboard alike, as both can start a connection.
    AdapterConnectDialogs(connectFlow)
}
