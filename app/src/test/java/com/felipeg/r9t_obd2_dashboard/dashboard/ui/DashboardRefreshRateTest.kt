package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardRefreshRateTest {
    private val hd90 = DisplayModeSpec(id = 1, width = 1080, height = 2400, refreshRate = 90f)
    private val hd60 = DisplayModeSpec(id = 2, width = 1080, height = 2400, refreshRate = 60f)
    private val hd120 = DisplayModeSpec(id = 3, width = 1080, height = 2400, refreshRate = 120f)
    private val sd60 = DisplayModeSpec(id = 4, width = 720, height = 1600, refreshRate = 60f)

    @Test
    fun picksSixtyHertzAtTheSameResolution() {
        assertEquals(hd60, DashboardRefreshRate.preferredMode(listOf(hd90, hd60, sd60), current = hd90))
        assertEquals(hd60, DashboardRefreshRate.preferredMode(listOf(hd120, hd90, hd60), current = hd120))
    }

    @Test
    fun neverChangesResolution() {
        assertNull(DashboardRefreshRate.preferredMode(listOf(hd90, sd60), current = hd90))
    }

    @Test
    fun leavesTheDisplayAloneWhenItAlreadyFits() {
        assertNull(DashboardRefreshRate.preferredMode(listOf(hd90, hd60), current = hd60))
    }

    @Test
    fun takesTheClosestRateAboveSixtyWhenThereIsNoSixty() {
        val hd72 = DisplayModeSpec(id = 5, width = 1080, height = 2400, refreshRate = 72f)
        assertEquals(hd72, DashboardRefreshRate.preferredMode(listOf(hd120, hd72), current = hd120))
    }

    @Test
    fun neverAsksForLessThanSixty() {
        val hd48 = DisplayModeSpec(id = 6, width = 1080, height = 2400, refreshRate = 48f)
        assertNull(DashboardRefreshRate.preferredMode(listOf(hd90, hd48), current = hd90))
    }
}
