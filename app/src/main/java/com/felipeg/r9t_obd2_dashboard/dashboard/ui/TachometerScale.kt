package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.GearPosition

/** Tachometer range shared by every dashboard layout, in thousands of RPM. */
internal object TachometerScale {
    const val MAX = 10f

    /** BMW Motorrad's R nineT Scrambler rider's manual lists a maximum of 8,500 rpm. */
    const val LIMIT_START = 8.5f

    /** Converts an RPM reading to the scale, clamped to its range. Null stays at zero. */
    fun fromRpm(rpm: Double?): Float = ((rpm ?: 0.0) / 1_000).toFloat().coerceIn(0f, MAX)

    fun isInLimitZone(thousands: Float): Boolean = thousands >= LIMIT_START
}

/** The single character a dashboard shows for a gear: N or the gear number. */
internal fun GearPosition.dashboardLabel(): String = when (this) {
    GearPosition.Neutral -> "N"
    is GearPosition.Forward -> number.toString()
}
