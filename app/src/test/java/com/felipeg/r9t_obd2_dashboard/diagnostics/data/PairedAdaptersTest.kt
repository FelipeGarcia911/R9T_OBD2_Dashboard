package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import android.bluetooth.BluetoothDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class PairedAdaptersTest {
    @Test
    fun keepsDevicesThatCanCarryAnRfcommLinkSortedByNameWithUnnamedLast() {
        val devices = listOf(
            BondedDevice("AA", "Vlink", BluetoothDevice.DEVICE_TYPE_CLASSIC),
            BondedDevice("BB", "Heart rate strap", BluetoothDevice.DEVICE_TYPE_LE),
            BondedDevice("CC", "OBDII", BluetoothDevice.DEVICE_TYPE_DUAL),
            BondedDevice("DD", null, BluetoothDevice.DEVICE_TYPE_UNKNOWN),
        )

        assertEquals(
            listOf(PairedAdapter("CC", "OBDII"), PairedAdapter("AA", "Vlink"), PairedAdapter("DD", null)),
            classicAdapters(devices),
        )
    }
}
