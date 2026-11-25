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
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
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
    val connectFlow = rememberAdapterConnectFlow(activity, container.pairedAdapters, model::connect)

    DiagnosticsOrDashboard(state.session, stringResource(R.string.diagnostics_source_live), modifier) { openDashboard ->
        Column(modifier) {
            ElmLogControls(container.logSettings, container.sessionLogs)
            if (onShowDemo != null) {
                OutlinedButton(onClick = {
                    model.disconnect()
                    onShowDemo()
                }) { Text(stringResource(R.string.show_simulation)) }
            }
            OutlinedButton(
                enabled = state.session is DiagnosticState.Live,
                onClick = openDashboard,
            ) { Text(stringResource(R.string.open_dashboard)) }
            DiagnosticScreen(
                state = state,
                trace = trace,
                onConnect = connectFlow::connect,
                onDisconnect = model::disconnect,
            )
        }
        AdapterConnectDialogs(connectFlow)
    }
}
