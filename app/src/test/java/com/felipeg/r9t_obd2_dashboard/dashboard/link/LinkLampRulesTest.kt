package com.felipeg.r9t_obd2_dashboard.dashboard.link

import com.felipeg.r9t_obd2_dashboard.dashboard.Blink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkLampRulesTest {
    @Test
    fun followsTftIndicatorConventions() {
        assertEquals(LinkLamp(LampColor.GREEN, blinkHz = 0, joined = true), LinkLampRules.lamp(LinkState.CONNECTED))
        assertEquals(LinkLamp(LampColor.AMBER, blinkHz = 1, joined = false), LinkLampRules.lamp(LinkState.CONNECTING))
        assertEquals(LinkLamp(LampColor.AMBER, blinkHz = 2, joined = false), LinkLampRules.lamp(LinkState.RECONNECTING))
        assertEquals(LinkLamp(LampColor.RED, blinkHz = 0, joined = false), LinkLampRules.lamp(LinkState.FAILED))
        assertEquals(LinkLamp(LampColor.DIM, blinkHz = 0, joined = false), LinkLampRules.lamp(LinkState.DISCONNECTED))
    }

    @Test
    fun onlyAnIdleOrFailedLinkOffersToConnect() {
        assertTrue(LinkLampRules.canConnect(LinkState.DISCONNECTED))
        assertTrue(LinkLampRules.canConnect(LinkState.FAILED))
        assertFalse(LinkLampRules.canConnect(LinkState.CONNECTED))
        assertFalse(LinkLampRules.canConnect(LinkState.CONNECTING))
        assertFalse(LinkLampRules.canConnect(LinkState.RECONNECTING))
    }

    @Test
    fun blinksStartLitAndSteadyLampsStayLit() {
        assertEquals(listOf(true, true, false, false), listOf(0L, 499L, 500L, 999L).map { Blink.isOn(1, it) })
        assertEquals(listOf(true, false, true), listOf(0L, 250L, 500L).map { Blink.isOn(2, it) })
        assertTrue(Blink.isOn(0, 12_345))
    }
}
