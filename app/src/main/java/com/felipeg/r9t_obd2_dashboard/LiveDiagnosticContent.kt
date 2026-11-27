package com.felipeg.r9t_obd2_dashboard

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.DashboardMenuItem
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.link.DashboardConnection
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.AdapterConnectDialogs
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.DiagnosticScreen
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.ElmLogControls
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.rememberAdapterConnectFlow
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.rememberDiagnosticViewModel

/**
 * The bike over a paired ELM327: the dashboard first, connecting on its own to the adapter chosen
 * last (the first time, the rider picks one), with diagnostics behind its menu. [onShowDemo], in
 * debug builds, switches to the simulation.
 */
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
    ConnectOnOpen(state.session, connectFlow::reconnect)
    val showDemo = onShowDemo?.let {
        {
            model.disconnect()
            it()
        }
    }
    val menuItems = buildList {
        add(DashboardMenuItem(stringResource(R.string.dashboard_menu_adapter), connectFlow::connect))
        showDemo?.let { add(DashboardMenuItem(stringResource(R.string.show_simulation), it)) }
    }

    DashboardWithDiagnostics(
        state.session, stringResource(R.string.diagnostics_source_live), connection, modifier, menuItems,
    ) { backToDashboard ->
        Column(modifier) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = backToDashboard) { Text(stringResource(R.string.back_to_dashboard)) }
                showDemo?.let { OutlinedButton(onClick = it) { Text(stringResource(R.string.show_simulation)) } }
            }
            ElmLogControls(container.logSettings, container.sessionLogs)
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
