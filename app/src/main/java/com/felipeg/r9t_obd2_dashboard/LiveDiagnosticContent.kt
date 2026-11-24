package com.felipeg.r9t_obd2_dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.DashboardScreen
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.ElmDiagnosticSession
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.ElmSessionLogs
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.ElmLogControls
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.ElmLogSettings
import java.io.File
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.RfcommElmTransport
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.DiagnosticScreen
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.DiagnosticViewModel

private const val CONNECT_PERMISSION = Manifest.permission.BLUETOOTH_CONNECT

private data class PairedAdapter(val id: String, val name: String)

@SuppressLint("MissingPermission")
@Composable
internal fun LiveDiagnosticContent(modifier: Modifier, onShowDemo: (() -> Unit)? = null) {
    val activity = LocalContext.current as ComponentActivity
    val application = activity.applicationContext
    val settings = remember(application) { ElmLogSettings(application) }
    val logs = remember(application) { ElmSessionLogs(File(application.noBackupFilesDir, "elm327-logs")) }
    val model = remember(activity) {
        val factory = DiagnosticViewModel.factory { scope ->
            ElmDiagnosticSession(scope, RfcommElmTransport(application)) {
                if (settings.detailedLogging) logs.start() else null
            }
        }
        ViewModelProvider(activity, factory).get("live-diagnostics", DiagnosticViewModel::class.java)
    }
    val lifecycle = activity.lifecycle
    val states = remember(model, lifecycle) { model.uiState.flowWithLifecycle(lifecycle) }
    val initialState = remember(model) { model.uiState.value }
    val state by states.collectAsState(initialState)
    val traces = remember(model, lifecycle) { model.trace.flowWithLifecycle(lifecycle) }
    val initialTrace = remember(model) { model.trace.value }
    val trace by traces.collectAsState(initialTrace)
    var showDashboard by rememberSaveable { mutableStateOf(false) }
    var adapters by remember { mutableStateOf<List<PairedAdapter>>(emptyList()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showPermissionSettings by remember { mutableStateOf(false) }

    fun loadAdapters() {
        try {
            val bluetooth = activity.getSystemService(BluetoothManager::class.java)?.adapter
            if (bluetooth == null) {
                notice = "This device does not support Bluetooth."
            } else if (!bluetooth.isEnabled) {
                notice = "Turn on Bluetooth in Android settings and try again."
            } else {
                adapters = bluetooth.bondedDevices
                    .filter { it.type != BluetoothDevice.DEVICE_TYPE_LE }
                    .map { PairedAdapter(it.address, it.name ?: "Unnamed device") }
                    .sortedBy { it.name }
                if (adapters.isEmpty()) {
                    notice = "Pair a Bluetooth Classic OBD-II adapter in Android settings first."
                }
            }
        } catch (_: SecurityException) {
            notice = "Allow Nearby devices access to connect to the adapter."
        }
    }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) loadAdapters() else {
            showPermissionSettings = !activity.shouldShowRequestPermissionRationale(CONNECT_PERMISSION)
            notice = "Nearby devices access is required to list paired adapters and connect."
        }
    }
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
        ElmLogControls(settings, logs)
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
            onConnect = {
                if (activity.checkSelfPermission(CONNECT_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
                    loadAdapters()
                } else if (activity.shouldShowRequestPermissionRationale(CONNECT_PERMISSION)) {
                    showPermissionSettings = false
                    notice = "Nearby devices access is required to list paired adapters and connect."
                } else {
                    requestPermission.launch(CONNECT_PERMISSION)
                }
            },
            onDisconnect = model::disconnect,
        )
    }

    if (adapters.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { adapters = emptyList() },
            title = { Text("Select paired OBD-II adapter") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    adapters.forEach { adapter ->
                        TextButton(onClick = {
                            adapters = emptyList()
                            model.connect(adapter.id)
                        }) { Text("${adapter.name} · ${adapter.id}") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { adapters = emptyList() }) { Text("Cancel") } },
        )
    }
    notice?.let { message ->
        val hasPermission = activity.checkSelfPermission(CONNECT_PERMISSION) == PackageManager.PERMISSION_GRANTED
        AlertDialog(
            onDismissRequest = { notice = null },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    notice = null
                    when {
                        hasPermission -> activity.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        showPermissionSettings -> activity.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", activity.packageName, null)),
                        )
                        else -> requestPermission.launch(CONNECT_PERMISSION)
                    }
                }) { Text(if (hasPermission || showPermissionSettings) "Open settings" else "Continue") }
            },
            dismissButton = { TextButton(onClick = { notice = null }) { Text("Cancel") } },
        )
    }
}
