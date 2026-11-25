package com.felipeg.r9t_obd2_dashboard.diagnostics.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.PairedAdapter
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.PairedAdapterResult
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.PairedAdapterSource

internal const val PERMISSION_REQUIRED_NOTICE =
    "Nearby devices access is required to list paired adapters and connect."

/** The Android side of connecting: the Nearby devices permission and the settings screens. */
internal interface ConnectPlatform {
    val hasPermission: Boolean

    /** True when Android would explain the permission instead of asking for it again. */
    val shouldShowRationale: Boolean
    fun requestPermission()
    fun openBluetoothSettings()
    fun openAppSettings()
}

/**
 * What the Connect button leads to: the permission request, the list of paired adapters to choose
 * from, or a [notice] explaining what the rider has to do first. The notice's button goes to the
 * screen that fixes it.
 */
internal class AdapterConnectFlow(
    private val platform: ConnectPlatform,
    private val adapters: PairedAdapterSource,
    private val onAdapterChosen: (adapterId: String) -> Unit,
) {
    var choices by mutableStateOf<List<PairedAdapter>>(emptyList())
        private set
    var notice by mutableStateOf<String?>(null)
        private set

    /** Android stopped asking for the permission, so only the app settings can grant it. */
    private var permissionOnlyInSettings by mutableStateOf(false)

    /** The notice's button opens a settings screen rather than asking again. */
    val noticeOpensSettings: Boolean get() = platform.hasPermission || permissionOnlyInSettings

    fun connect() {
        when {
            platform.hasPermission -> showAdapters()
            platform.shouldShowRationale -> {
                permissionOnlyInSettings = false
                notice = PERMISSION_REQUIRED_NOTICE
            }
            else -> platform.requestPermission()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            showAdapters()
        } else {
            permissionOnlyInSettings = !platform.shouldShowRationale
            notice = PERMISSION_REQUIRED_NOTICE
        }
    }

    fun choose(adapter: PairedAdapter) {
        choices = emptyList()
        onAdapterChosen(adapter.id)
    }

    fun dismissChoices() {
        choices = emptyList()
    }

    fun dismissNotice() {
        notice = null
    }

    fun confirmNotice() {
        notice = null
        when {
            platform.hasPermission -> platform.openBluetoothSettings()
            permissionOnlyInSettings -> platform.openAppSettings()
            else -> platform.requestPermission()
        }
    }

    private fun showAdapters() {
        when (val result = adapters.pairedAdapters()) {
            is PairedAdapterResult.Found -> choices = result.adapters
            else -> notice = result.notice()
        }
    }
}

private fun PairedAdapterResult.notice(): String = when (this) {
    is PairedAdapterResult.Found -> error("Adapters found")
    PairedAdapterResult.NonePaired -> "Pair a Bluetooth Classic OBD-II adapter in Android settings first."
    PairedAdapterResult.BluetoothOff -> "Turn on Bluetooth in Android settings and try again."
    PairedAdapterResult.BluetoothUnsupported -> "This device does not support Bluetooth."
    PairedAdapterResult.PermissionMissing -> "Allow Nearby devices access to connect to the adapter."
}
