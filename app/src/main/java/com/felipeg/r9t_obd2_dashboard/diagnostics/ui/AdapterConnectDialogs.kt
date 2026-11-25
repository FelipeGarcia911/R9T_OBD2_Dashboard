package com.felipeg.r9t_obd2_dashboard.diagnostics.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.felipeg.r9t_obd2_dashboard.R
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.PairedAdapterSource

private const val CONNECT_PERMISSION = Manifest.permission.BLUETOOTH_CONNECT

private class ActivityConnectPlatform(private val activity: ComponentActivity) : ConnectPlatform {
    var launcher: ActivityResultLauncher<String>? = null

    override val hasPermission: Boolean
        get() = activity.checkSelfPermission(CONNECT_PERMISSION) == PackageManager.PERMISSION_GRANTED

    override val shouldShowRationale: Boolean
        get() = activity.shouldShowRequestPermissionRationale(CONNECT_PERMISSION)

    override fun requestPermission() {
        launcher?.launch(CONNECT_PERMISSION)
    }

    override fun openBluetoothSettings() {
        activity.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    override fun openAppSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
        )
    }
}

@Composable
internal fun rememberAdapterConnectFlow(
    activity: ComponentActivity,
    adapters: PairedAdapterSource,
    onAdapterChosen: (adapterId: String) -> Unit,
): AdapterConnectFlow {
    val platform = remember(activity) { ActivityConnectPlatform(activity) }
    val flow = remember(platform, adapters) { AdapterConnectFlow(platform, adapters, onAdapterChosen) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        flow.onPermissionResult(granted)
    }
    SideEffect { platform.launcher = launcher }
    return flow
}

/** The adapter list and the notice dialogs, shown while [flow] has something to ask. */
@Composable
internal fun AdapterConnectDialogs(flow: AdapterConnectFlow) {
    if (flow.choices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = flow::dismissChoices,
            title = { Text(stringResource(R.string.connect_select_adapter)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    flow.choices.forEach { adapter ->
                        val name = adapter.name ?: stringResource(R.string.connect_unnamed_device)
                        TextButton(onClick = { flow.choose(adapter) }) {
                            Text(stringResource(R.string.connect_adapter_choice, name, adapter.id))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = flow::dismissChoices) { Text(stringResource(R.string.cancel)) } },
        )
    }
    flow.notice?.let { notice ->
        AlertDialog(
            onDismissRequest = flow::dismissNotice,
            text = { Text(stringResource(notice.message)) },
            confirmButton = {
                TextButton(onClick = flow::confirmNotice) {
                    Text(stringResource(if (flow.noticeOpensSettings) R.string.open_settings else R.string.continue_action))
                }
            },
            dismissButton = { TextButton(onClick = flow::dismissNotice) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private val ConnectNotice.message: Int
    get() = when (this) {
        ConnectNotice.PERMISSION_REQUIRED -> R.string.connect_permission_required
        ConnectNotice.NONE_PAIRED -> R.string.connect_none_paired
        ConnectNotice.BLUETOOTH_OFF -> R.string.connect_bluetooth_off
        ConnectNotice.BLUETOOTH_UNSUPPORTED -> R.string.connect_bluetooth_unsupported
        ConnectNotice.PERMISSION_MISSING -> R.string.connect_permission_missing
    }
