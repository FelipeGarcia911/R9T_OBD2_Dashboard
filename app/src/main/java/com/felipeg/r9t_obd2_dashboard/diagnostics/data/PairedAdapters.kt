package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context

/** A Bluetooth device paired with the phone that may be an ELM327 adapter. */
internal data class PairedAdapter(val id: String, val name: String)

/** What the phone can offer when the rider asks to connect. */
internal sealed interface PairedAdapterResult {
    /** At least one adapter to choose from. */
    data class Found(val adapters: List<PairedAdapter>) : PairedAdapterResult
    data object NonePaired : PairedAdapterResult
    data object BluetoothOff : PairedAdapterResult
    data object BluetoothUnsupported : PairedAdapterResult
    data object PermissionMissing : PairedAdapterResult
}

internal fun interface PairedAdapterSource {
    fun pairedAdapters(): PairedAdapterResult
}

/** A bonded device as Android reports it; [type] is one of the `BluetoothDevice.DEVICE_TYPE_` values. */
internal data class BondedDevice(val address: String, val name: String?, val type: Int)

/**
 * The devices that can carry an RFCOMM link, sorted by name. Bluetooth Low Energy-only devices
 * cannot, so they are left out.
 */
internal fun classicAdapters(devices: Collection<BondedDevice>): List<PairedAdapter> = devices
    .filter { it.type != BluetoothDevice.DEVICE_TYPE_LE }
    .map { PairedAdapter(it.address, it.name ?: "Unnamed device") }
    .sortedBy { it.name }

internal class BluetoothPairedAdapters(private val context: Context) : PairedAdapterSource {
    @SuppressLint("MissingPermission") // A missing permission throws SecurityException, handled below.
    override fun pairedAdapters(): PairedAdapterResult = try {
        val bluetooth = context.getSystemService(BluetoothManager::class.java)?.adapter
        when {
            bluetooth == null -> PairedAdapterResult.BluetoothUnsupported
            !bluetooth.isEnabled -> PairedAdapterResult.BluetoothOff
            else -> classicAdapters(bluetooth.bondedDevices.map { BondedDevice(it.address, it.name, it.type) })
                .let { if (it.isEmpty()) PairedAdapterResult.NonePaired else PairedAdapterResult.Found(it) }
        }
    } catch (_: SecurityException) {
        PairedAdapterResult.PermissionMissing
    }
}
