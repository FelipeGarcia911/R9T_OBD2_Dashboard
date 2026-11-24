package com.felipeg.r9t_obd2_dashboard

import androidx.compose.runtime.Composable
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
    val activity = LocalContext.current as ComponentActivity
    val lifecycle = activity.lifecycle
    val model = remember(activity) {
        ViewModelProvider(activity, DiagnosticViewModel.factory(::SimulatedDiagnosticSession))
            .get(DiagnosticViewModel::class.java)
    }
    val states = remember(model, lifecycle) { model.uiState.flowWithLifecycle(lifecycle) }
    val state by states.collectAsState(initial = model.uiState.value)
    val traceStates = remember(model, lifecycle) { model.trace.flowWithLifecycle(lifecycle) }
    val trace by traceStates.collectAsState(initial = model.trace.value)
    DiagnosticScreen(
        state = state,
        trace = trace,
        onConnect = { model.connect("demo") },
        onDisconnect = model::disconnect,
        onSimulateTimeout = { model.connect("demo-timeout") },
        modifier = modifier,
    )
}
