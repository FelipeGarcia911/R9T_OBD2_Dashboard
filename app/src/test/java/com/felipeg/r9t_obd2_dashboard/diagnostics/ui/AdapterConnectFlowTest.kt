package com.felipeg.r9t_obd2_dashboard.diagnostics.ui

import com.felipeg.r9t_obd2_dashboard.diagnostics.data.PairedAdapter
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.PairedAdapterResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdapterConnectFlowTest {
    private val elm = PairedAdapter("00:11:22:33:44:55", "OBDII")
    private val platform = FakePlatform()
    private var result: PairedAdapterResult = PairedAdapterResult.Found(listOf(elm))
    private val chosen = mutableListOf<String>()
    private val flow = AdapterConnectFlow(platform, { result }, { chosen += it })

    @Test
    fun withPermissionListsAdaptersAndConnectsToTheChosenOne() {
        platform.hasPermission = true

        flow.connect()
        assertEquals(listOf(elm), flow.choices)

        flow.choose(elm)
        assertEquals(listOf(elm.id), chosen)
        assertTrue(flow.choices.isEmpty())
    }

    @Test
    fun withoutPermissionAsksForItAndListsAdaptersOnceGranted() {
        flow.connect()
        assertEquals(listOf("request"), platform.calls)

        platform.hasPermission = true
        flow.onPermissionResult(granted = true)
        assertEquals(listOf(elm), flow.choices)
    }

    @Test
    fun afterARefusalAndroidCanStillAskSoTheNoticeContinues() {
        platform.shouldShowRationale = true
        flow.onPermissionResult(granted = false)

        assertEquals(ConnectNotice.PERMISSION_REQUIRED, flow.notice)
        assertFalse(flow.noticeOpensSettings)
        flow.confirmNotice()
        assertEquals(listOf("request"), platform.calls)
        assertNull(flow.notice)
    }

    @Test
    fun whenAndroidStopsAskingTheNoticeOpensAppSettings() {
        platform.shouldShowRationale = false
        flow.onPermissionResult(granted = false)

        assertTrue(flow.noticeOpensSettings)
        flow.confirmNotice()
        assertEquals(listOf("app settings"), platform.calls)
    }

    @Test
    fun aRationaleOnConnectExplainsBeforeAsking() {
        platform.shouldShowRationale = true

        flow.connect()

        assertEquals(ConnectNotice.PERMISSION_REQUIRED, flow.notice)
        assertTrue(platform.calls.isEmpty())
        flow.confirmNotice()
        assertEquals(listOf("request"), platform.calls)
    }

    @Test
    fun bluetoothProblemsPointToBluetoothSettings() {
        platform.hasPermission = true
        mapOf(
            PairedAdapterResult.BluetoothOff to ConnectNotice.BLUETOOTH_OFF,
            PairedAdapterResult.NonePaired to ConnectNotice.NONE_PAIRED,
            PairedAdapterResult.BluetoothUnsupported to ConnectNotice.BLUETOOTH_UNSUPPORTED,
            PairedAdapterResult.PermissionMissing to ConnectNotice.PERMISSION_MISSING,
        ).forEach { (problem, notice) ->
            result = problem
            flow.connect()
            assertEquals(notice, flow.notice)
            assertTrue(flow.choices.isEmpty())
            assertTrue(flow.noticeOpensSettings)
        }
        flow.confirmNotice()
        assertEquals(listOf("bluetooth settings"), platform.calls)
    }

    @Test
    fun dismissingClosesWithoutConnecting() {
        platform.hasPermission = true
        flow.connect()
        flow.dismissChoices()
        assertTrue(flow.choices.isEmpty())

        result = PairedAdapterResult.BluetoothOff
        flow.connect()
        flow.dismissNotice()
        assertNull(flow.notice)
        assertTrue(chosen.isEmpty())
        assertTrue(platform.calls.isEmpty())
    }

    private class FakePlatform : ConnectPlatform {
        override var hasPermission = false
        override var shouldShowRationale = false
        val calls = mutableListOf<String>()
        override fun requestPermission() { calls += "request" }
        override fun openBluetoothSettings() { calls += "bluetooth settings" }
        override fun openAppSettings() { calls += "app settings" }
    }
}
