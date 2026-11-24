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

@Composable
internal fun DiagnosticScreen(
    state: DiagnosticUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    onSimulateTimeout: (() -> Unit)? = null,
    trace: List<DiagnosticTraceEntry> = emptyList(),
    sourceLabel: String? = if (onSimulateTimeout != null) "Simulated telemetry" else null,
    connectLabel: String = "Connect",
) {
    val session = state.session
    val live = session as? DiagnosticState.Live
    val canConnect = session is DiagnosticState.Disconnected || session is DiagnosticState.Failed
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Diagnostics", style = MaterialTheme.typography.headlineLarge)
        if (sourceLabel != null) {
            Text(sourceLabel, style = MaterialTheme.typography.labelLarge)
        }
        Text("BMW R nineT Scrambler · 2023")
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Session", style = MaterialTheme.typography.titleMedium)
                Text(session.statusText())
                Text("Protocol: ${live?.protocol ?: "Unavailable"}")
            }
        }
        ReadingCard("Engine speed", live?.engineRpm, "rpm", state.rpmHz)
        ReadingCard("Vehicle speed", live?.speedKmh, "km/h", state.speedHz)
        val gear = when (val position = live?.gear) {
            GearPosition.Neutral -> "N"
            is GearPosition.Forward -> position.number.toString()
            null -> "Unavailable"
        }
        ValueCard("Gear", gear)
        ValueCard("Local time", rememberDashboardClock())
        val distance = live?.odometerKm as? SignalReading.Sample
        ValueCard("Odometer", distance?.let { "${format(it.value)} km" } ?: "Unavailable")
        if (canConnect) {
            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text(connectLabel) }
            if (onSimulateTimeout != null) {
                OutlinedButton(onClick = onSimulateTimeout, modifier = Modifier.fillMaxWidth()) {
                    Text("Simulate ECU timeout")
                }
            }
        } else {
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                Text("Disconnect")
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
    Text("OBD-II session log", style = MaterialTheme.typography.titleLarge)
    Text("0100 · Supported PIDs   010C · Engine RPM   010D · Vehicle speed")
    Text("RPM = (256 × A + B) / 4. Speed = A km/h. A and B are hexadecimal data bytes after the response PID.")
    if (entries.isEmpty()) {
        Text("Connect to record session events.")
        return
    }
    OutlinedButton(onClick = {
        val report = entries.joinToString("\n") { it.logText() }
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("OBD-II session log", report))) }
    }) {
        Text("Copy session log")
    }
    Text("Latest 12 entries · Copy includes retained session history")
    entries.takeLast(12).asReversed().forEach { entry ->
        Text(entry.logText(), style = MaterialTheme.typography.bodySmall)
    }
}

private val logTime = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT)

private fun DiagnosticTraceEntry.logText(): String = buildString {
    append(logTime.format(Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault())))
    append(" · ").append(stage.label()).append(" · ").append(description)
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
                sample?.let { "${format(it.value)} $unit" } ?: "Unavailable",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(rate?.let { "${format(it)} Hz" } ?: "Update rate unavailable")
        }
    }
}

private fun format(number: Double): String =
    String.format(Locale.US, "%.2f", number).trimEnd('0').trimEnd('.')

private fun DiagnosticState.statusText(): String = when (this) {
    DiagnosticState.Disconnected -> "Disconnected"
    DiagnosticState.ConnectingBluetooth -> "Connecting over Bluetooth…"
    DiagnosticState.WaitingForAdapter -> "Bluetooth connected · Waiting for ELM327…"
    DiagnosticState.WaitingForEcu -> "ELM327 responded · Waiting for ECU…"
    is DiagnosticState.Live -> "ECU responding"
    is DiagnosticState.Failed -> "${stage.label()}: ${error.label()}"
}

private fun DiagnosticStage.label(): String = when (this) {
    DiagnosticStage.BLUETOOTH -> "Bluetooth"
    DiagnosticStage.ADAPTER -> "ELM327"
    DiagnosticStage.ECU -> "ECU"
    DiagnosticStage.POLLING -> "Live readings"
}

private fun DiagnosticError.label(): String = when (this) {
    DiagnosticError.PERMISSION_DENIED -> "Permission denied"
    DiagnosticError.BLUETOOTH_DISABLED -> "Bluetooth disabled"
    DiagnosticError.ADAPTER_UNREACHABLE -> "Adapter unreachable. Check power and close other OBD-II apps."
    DiagnosticError.TIMEOUT -> "Response timed out"
    DiagnosticError.LOG_WRITE_FAILED -> "Could not save the session log. Check available storage."
    DiagnosticError.NO_ECU_DATA -> "No ECU data. Check the ignition and diagnostic connector."
    DiagnosticError.INVALID_RESPONSE -> "Invalid response"
    DiagnosticError.CONNECTION_LOST -> "Connection lost"
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
