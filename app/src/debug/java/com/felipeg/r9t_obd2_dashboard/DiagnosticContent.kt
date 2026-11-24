package com.felipeg.r9t_obd2_dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.DiagnosticScreen
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.DiagnosticViewModel
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.SimulatedDiagnosticSession

@Composable
internal fun DiagnosticContent(modifier: Modifier) {
    var showDemo by rememberSaveable { mutableStateOf(false) }
    if (showDemo) {
        SimulatedDiagnosticContent(modifier, onShowLive = { showDemo = false })
    } else {
        LiveDiagnosticContent(modifier, onShowDemo = { showDemo = true })
    }
}

@Composable
private fun SimulatedDiagnosticContent(modifier: Modifier, onShowLive: () -> Unit) {
    val activity = LocalContext.current as ComponentActivity
    val lifecycle = activity.lifecycle
    val model = remember(activity) {
        ViewModelProvider(activity, DiagnosticViewModel.factory(::SimulatedDiagnosticSession))
            .get(DiagnosticViewModel::class.java)
    }
    val states = remember(model, lifecycle) { model.uiState.flowWithLifecycle(lifecycle) }
    val initialState = remember(model) { model.uiState.value }
    val state by states.collectAsState(initialState)
    val traceStates = remember(model, lifecycle) { model.trace.flowWithLifecycle(lifecycle) }
    val initialTrace = remember(model) { model.trace.value }
    val trace by traceStates.collectAsState(initialTrace)
    Column(modifier) {
        OutlinedButton(onClick = {
            model.disconnect()
            onShowLive()
        }) { Text("Show live diagnostics") }
        DiagnosticScreen(
            state = state,
            trace = trace,
            onConnect = { model.connect("demo") },
            onDisconnect = model::disconnect,
            onSimulateTimeout = { model.connect("demo-timeout") },
        )
    }
}
