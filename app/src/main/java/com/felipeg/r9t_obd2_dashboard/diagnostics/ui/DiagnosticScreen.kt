package com.felipeg.r9t_obd2_dashboard.diagnostics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading
import com.felipeg.r9t_obd2_dashboard.ui.theme.R9T_OBD2_DashboardTheme
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticTraceEntry
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.GearPosition
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.felipeg.r9t_obd2_dashboard.R

@Composable
internal fun DiagnosticScreen(
    state: DiagnosticUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    onSimulateTimeout: (() -> Unit)? = null,
    trace: List<DiagnosticTraceEntry> = emptyList(),
    sourceLabel: String? = null,
    connectLabel: String = stringResource(R.string.diagnostics_connect),
) {
    val session = state.session
    val live = session as? DiagnosticState.Live
    val canConnect = session is DiagnosticState.Disconnected || session is DiagnosticState.Failed
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.diagnostics_title), style = MaterialTheme.typography.headlineLarge)
        if (sourceLabel != null) {
            Text(sourceLabel, style = MaterialTheme.typography.labelLarge)
        }
        Text(stringResource(R.string.diagnostics_vehicle))
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.diagnostics_session), style = MaterialTheme.typography.titleMedium)
                Text(session.statusText())
                Text(stringResource(R.string.diagnostics_protocol, live?.protocol ?: stringResource(R.string.unavailable)))
            }
        }
        ReadingCard(stringResource(R.string.diagnostics_engine_speed), live?.engineRpm, "rpm", state.rpmHz)
        ReadingCard(stringResource(R.string.diagnostics_vehicle_speed), live?.speedKmh, "km/h", state.speedHz)
        val gear = when (val position = live?.gear) {
            GearPosition.Neutral -> stringResource(R.string.diagnostics_gear_neutral)
            is GearPosition.Forward -> position.number.toString()
            null -> stringResource(R.string.unavailable)
        }
        ValueCard(stringResource(R.string.diagnostics_gear), gear)
        ValueCard(stringResource(R.string.diagnostics_local_time), rememberDashboardClock())
        val distance = live?.odometerKm as? SignalReading.Sample
        ValueCard(
            stringResource(R.string.diagnostics_odometer),
            distance?.let { "${format(it.value)} km" } ?: stringResource(R.string.unavailable),
        )
        if (canConnect) {
            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text(connectLabel) }
            if (onSimulateTimeout != null) {
                OutlinedButton(onClick = onSimulateTimeout, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.diagnostics_simulate_timeout))
                }
            }
        } else {
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.diagnostics_disconnect))
            }
        }
        SessionLog(trace)
    }
}

@Composable
private fun ValueCard(title: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun SessionLog(entries: List<DiagnosticTraceEntry>) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.log_title)
    val stageLabels = DiagnosticStage.entries.associateWith { stringResource(it.label) }
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.log_pid_legend))
    Text(stringResource(R.string.log_formula))
    if (entries.isEmpty()) {
        Text(stringResource(R.string.log_empty))
        return
    }
    OutlinedButton(onClick = {
        val report = entries.joinToString("\n") { it.logText(stageLabels) }
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(title, report))) }
    }) {
        Text(stringResource(R.string.log_copy))
    }
    Text(stringResource(R.string.log_latest))
    entries.takeLast(12).asReversed().forEach { entry ->
        Text(entry.logText(stageLabels), style = MaterialTheme.typography.bodySmall)
    }
}

private val logTime = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT)

private fun DiagnosticTraceEntry.logText(stageLabels: Map<DiagnosticStage, String>): String = buildString {
    append(logTime.format(Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault())))
    append(" · ").append(stageLabels.getValue(stage)).append(" · ").append(description)
    command?.let { append(" · TX ").append(it) }
    response?.let { append(" · RX ").append(it) }
}

@Composable
private fun ReadingCard(title: String, reading: SignalReading?, unit: String, rate: Double?) {
    val sample = reading as? SignalReading.Sample
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                sample?.let { "${format(it.value)} $unit" } ?: stringResource(R.string.unavailable),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(rate?.let { "${format(it)} Hz" } ?: stringResource(R.string.diagnostics_update_rate_unavailable))
        }
    }
}

private fun format(number: Double): String =
    String.format(Locale.US, "%.2f", number).trimEnd('0').trimEnd('.')

@Composable
private fun DiagnosticState.statusText(): String = when (this) {
    DiagnosticState.Disconnected -> stringResource(R.string.status_disconnected)
    DiagnosticState.ConnectingBluetooth -> stringResource(R.string.status_connecting_bluetooth)
    DiagnosticState.WaitingForAdapter -> stringResource(R.string.status_waiting_for_adapter)
    DiagnosticState.WaitingForEcu -> stringResource(R.string.status_waiting_for_ecu)
    is DiagnosticState.Live -> stringResource(R.string.status_live)
    is DiagnosticState.Reconnecting -> stringResource(R.string.status_reconnecting, stringResource(cause.label), attempt)
    is DiagnosticState.Failed -> stringResource(R.string.status_failed, stringResource(stage.label), stringResource(error.label))
}

private val DiagnosticStage.label: Int
    get() = when (this) {
        DiagnosticStage.BLUETOOTH -> R.string.stage_bluetooth
        DiagnosticStage.ADAPTER -> R.string.stage_adapter
        DiagnosticStage.ECU -> R.string.stage_ecu
        DiagnosticStage.POLLING -> R.string.stage_polling
    }

private val DiagnosticError.label: Int
    get() = when (this) {
        DiagnosticError.PERMISSION_DENIED -> R.string.error_permission_denied
        DiagnosticError.BLUETOOTH_DISABLED -> R.string.error_bluetooth_disabled
        DiagnosticError.ADAPTER_UNREACHABLE -> R.string.error_adapter_unreachable
        DiagnosticError.TIMEOUT -> R.string.error_timeout
        DiagnosticError.LOG_WRITE_FAILED -> R.string.error_log_write_failed
        DiagnosticError.NO_ECU_DATA -> R.string.error_no_ecu_data
        DiagnosticError.INVALID_RESPONSE -> R.string.error_invalid_response
        DiagnosticError.CONNECTION_LOST -> R.string.error_connection_lost
        DiagnosticError.COMMAND_BLOCKED -> R.string.error_command_blocked
    }

@Preview(showBackground = true)
@Composable
private fun LiveDiagnosticPreview() {
    R9T_OBD2_DashboardTheme {
        DiagnosticScreen(
            state = DiagnosticUiState(
                session = DiagnosticState.Live(
                    protocol = "Simulated CAN",
                    engineRpm = SignalReading.Sample(1_100.0, 0),
                    speedKmh = SignalReading.Sample(0.0, 0),
                    gear = GearPosition.Neutral,
                    odometerKm = SignalReading.Sample(12_480.0, 0),
                ),
                rpmHz = 2.0,
                speedHz = 1.0,
            ),
            onConnect = {},
            onDisconnect = {},
            onSimulateTimeout = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DisconnectedDiagnosticPreview() {
    R9T_OBD2_DashboardTheme {
        DiagnosticScreen(DiagnosticUiState(), {}, {}, onSimulateTimeout = {})
    }
}
