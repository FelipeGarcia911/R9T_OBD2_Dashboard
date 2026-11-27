package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the layered drawing: while the reading moves, only the reading layer redraws; the scale,
 * ticks and numerals are drawn once and reused from their cached layer.
 */
@RunWith(AndroidJUnit4::class)
class DashboardLayersTest {
    @Test
    fun sportRedrawsOnlyTheNeedle() = assertOnlyReadingRedraws(DashboardLayout.SPORT)

    private fun assertOnlyReadingRedraws(layout: DashboardLayout) {
        val draws = ConcurrentHashMap<RenderLayer, AtomicInteger>()
        val probe = RenderProbe { layer -> draws.getOrPut(layer) { AtomicInteger() }.incrementAndGet() }

        runDashboard(layout, durationMillis = 3_000, probe = probe)

        val scale = draws[RenderLayer.SCALE]?.get() ?: 0
        val reading = draws[RenderLayer.READING]?.get() ?: 0
        // ~45 readings in 3 s, each moving the needle over several frames.
        assertTrue("$layout reading layer drew $reading times", reading >= 30)
        // Once, plus at most a couple of redraws while the window settles into landscape.
        assertTrue("$layout scale drew $scale times; it should stay cached", scale <= 3)
    }
}
