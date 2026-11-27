package com.felipeg.r9t_obd2_dashboard

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.DashboardMenuItem
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.link.DashboardConnection
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.ReplayDiagnosticSession
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.SimulatedDiagnosticSession
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.DiagnosticScreen
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.rememberDiagnosticViewModel

/** The bike, as in release builds, with the simulation one menu entry away. */
@Composable
internal fun DiagnosticContent(modifier: Modifier) {
    var showDemo by rememberSaveable { mutableStateOf(false) }
    if (showDemo) {
        SimulatedDiagnosticContent(modifier, onShowLive = { showDemo = false })
    } else {
        LiveDiagnosticContent(modifier, onShowDemo = { showDemo = true })
    }
}

/**
 * The dashboard fed by the bundled real capture (looping, with the ECU's real timing), the
 * synthetic ride or a capture the rider opens. It starts playing at once; the menu switches
 * between sources and back to the bike.
 */
@Composable
private fun SimulatedDiagnosticContent(modifier: Modifier, onShowLive: () -> Unit) {
    val activity = LocalContext.current as ComponentActivity
    val application = activity.applicationContext
    // Null plays the synthetic ride; anything else is a capture to replay.
    var capture by rememberSaveable { mutableStateOf<String?>(ReplayDiagnosticSession.SAMPLE_CAPTURE) }
    val simulation = rememberDiagnosticViewModel(activity, "simulated-diagnostics", ::SimulatedDiagnosticSession)
    val replay = rememberDiagnosticViewModel(activity, "replay-diagnostics") { scope ->
        ReplayDiagnosticSession(scope, application)
    }
    val model = if (capture == null) simulation else replay
    val adapterId = capture ?: SYNTHETIC
    val state by model.uiState.collectAsStateWithLifecycle()
    val trace by model.trace.collectAsStateWithLifecycle()
    ConnectOnOpen(state.session) { model.connect(adapterId) }

    fun play(source: String?) {
        model.disconnect()
        capture = source
        if (source == null) simulation.restart(SYNTHETIC) else replay.restart(source)
    }
    val openCapture = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) play(uri.toString())
    }
    val backToBike = {
        model.disconnect()
        onShowLive()
    }
    val switchLabel = stringResource(if (capture == null) R.string.debug_example_capture else R.string.debug_synthetic_data)
    val switchSource = { play(if (capture == null) ReplayDiagnosticSession.SAMPLE_CAPTURE else null) }
    val openLabel = stringResource(R.string.debug_open_capture)
    val openFile = { openCapture.launch(arrayOf("*/*")) }
    val backLabel = stringResource(R.string.debug_back_to_bike)
    val menuItems = listOf(
        DashboardMenuItem(switchLabel, switchSource),
        DashboardMenuItem(openLabel, openFile),
        DashboardMenuItem(backLabel, backToBike),
    )
    val sourceLabel = stringResource(if (capture == null) R.string.debug_source_simulation else R.string.debug_source_capture_replay)
    val connection = DashboardConnection(connect = { model.connect(adapterId) }, disconnect = model::disconnect)

    DashboardWithDiagnostics(state.session, sourceLabel, connection, modifier, menuItems) { backToDashboard ->
        Column(modifier) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = backToDashboard) { Text(stringResource(R.string.back_to_dashboard)) }
                OutlinedButton(onClick = switchSource) { Text(switchLabel) }
                OutlinedButton(onClick = openFile) { Text(openLabel) }
                OutlinedButton(onClick = backToBike) { Text(backLabel) }
            }
            DiagnosticScreen(
                state = state,
                trace = trace,
                onConnect = { model.connect(adapterId) },
                onDisconnect = model::disconnect,
                onSimulateTimeout = if (capture == null) ({ simulation.restart("demo-timeout") }) else null,
                sourceLabel = stringResource(
                    if (capture == null) R.string.diagnostics_source_simulated else R.string.debug_source_capture_replay,
                ),
                connectLabel = stringResource(if (capture == null) R.string.diagnostics_connect else R.string.debug_play_capture),
            )
        }
    }
}

private const val SYNTHETIC = "demo"
