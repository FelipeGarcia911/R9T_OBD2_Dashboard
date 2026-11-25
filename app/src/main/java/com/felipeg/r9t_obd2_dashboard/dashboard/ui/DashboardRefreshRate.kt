package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import kotlin.math.abs

/** A display mode as Android lists it: its id, resolution and refresh rate. */
internal data class DisplayModeSpec(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

/**
 * The dashboard runs at 60 Hz. Readings arrive ~15 times a second, so 60 frames keep the needle
 * smooth, while a 90 or 120 Hz screen leaves the GPU too little time per frame on mid-range
 * phones (measured on the reference Moto G30: ~15 ms of GPU work against 11 ms at 90 Hz).
 */
internal object DashboardRefreshRate {
    const val TARGET_HZ = 60f

    /**
     * The mode to ask for: same resolution as [current], refresh rate closest to [TARGET_HZ] and
     * not below it. Null when the current mode already fits or no such mode exists.
     */
    fun preferredMode(modes: List<DisplayModeSpec>, current: DisplayModeSpec): DisplayModeSpec? {
        val candidates = modes.filter {
            it.width == current.width && it.height == current.height && it.refreshRate >= TARGET_HZ - 1
        }
        val best = candidates.minByOrNull { abs(it.refreshRate - TARGET_HZ) } ?: return null
        return best.takeIf { it.id != current.id && it.refreshRate < current.refreshRate }
    }
}
