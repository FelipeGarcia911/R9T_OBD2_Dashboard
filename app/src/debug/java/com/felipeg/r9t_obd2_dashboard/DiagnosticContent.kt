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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.dp
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.ReplayDiagnosticSession
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.DashboardScreen
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.DiagnosticScreen
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.DiagnosticViewModel
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.SimulatedDiagnosticSession

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
    var capture by rememberSaveable { mutableStateOf<String?>(null) }
    val application = activity.applicationContext
    val simulation = remember(activity) {
        ViewModelProvider(activity, DiagnosticViewModel.factory(::SimulatedDiagnosticSession))
            .get("simulated-diagnostics", DiagnosticViewModel::class.java)
    }
    val replay = remember(activity) {
        val factory = DiagnosticViewModel.factory { scope -> ReplayDiagnosticSession(scope, application) }
        ViewModelProvider(activity, factory).get("replay-diagnostics", DiagnosticViewModel::class.java)
    }
    val model = if (capture == null) simulation else replay
    val adapterId = capture ?: "demo"
    val openCapture = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            model.disconnect()
            capture = uri.toString()
        }
    }
    val states = remember(model, lifecycle) { model.uiState.flowWithLifecycle(lifecycle) }
    val initialState = remember(model) { model.uiState.value }
    val state by states.collectAsState(initialState)
    var showDashboard by rememberSaveable { mutableStateOf(false) }
    if (showDashboard) {
        BackHandler { showDashboard = false }
        DashboardScreen(
            state.session,
            onDiagnostics = { showDashboard = false },
            modifier = modifier,
            sourceLabel = if (capture == null) "Simulation" else "Capture replay",
        )
        return
    }
    val traceStates = remember(model, lifecycle) { model.trace.flowWithLifecycle(lifecycle) }
    val initialTrace = remember(model) { model.trace.value }
    val trace by traceStates.collectAsState(initialTrace)
    Column(modifier) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = {
                model.disconnect()
                onShowLive()
            }) { Text("Show live diagnostics") }
            OutlinedButton(onClick = {
                if (state.session is DiagnosticState.Disconnected || state.session is DiagnosticState.Failed) {
                    model.connect(adapterId)
                }
                showDashboard = true
            }) { Text("Open dashboard") }
            if (capture == null) {
                OutlinedButton(onClick = {
                    simulation.disconnect()
                    capture = ReplayDiagnosticSession.SAMPLE_CAPTURE
                }) { Text("Example capture") }
            } else {
                OutlinedButton(onClick = {
                    replay.disconnect()
                    capture = null
                }) { Text("Show simulation") }
            }
            OutlinedButton(onClick = { openCapture.launch(arrayOf("*/*")) }) { Text("Open capture") }
        }
        DiagnosticScreen(
            state = state,
            trace = trace,
            onConnect = { model.connect(adapterId) },
            onDisconnect = model::disconnect,
            onSimulateTimeout = if (capture == null) ({ simulation.connect("demo-timeout") }) else null,
            sourceLabel = if (capture == null) "Simulated telemetry" else "Capture replay",
            connectLabel = if (capture == null) "Connect" else "Play capture",
        )
    }
}
