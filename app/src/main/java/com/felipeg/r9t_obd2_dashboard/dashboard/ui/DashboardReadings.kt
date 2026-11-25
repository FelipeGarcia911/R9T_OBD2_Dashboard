package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.GearPosition
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading

/**
 * Everything a dashboard display shows, read once from the session so displays only draw.
 * A null reading has not arrived; [status] describes the connection while it is not live.
 */
internal data class DashboardReadings(
    val rpm: Double? = null,
    val speedKmh: Double? = null,
    val gear: GearPosition? = null,
    val odometerKm: Double? = null,
    val vitals: EngineVitals = EngineVitals.Unavailable,
    val status: String? = null,
) {
    companion object {
        fun from(state: DiagnosticState): DashboardReadings = when (state) {
            is DiagnosticState.Live -> DashboardReadings(
                rpm = state.engineRpm.valueOrNull(),
                speedKmh = state.speedKmh.valueOrNull(),
                gear = state.gear,
                odometerKm = state.odometerKm.valueOrNull(),
                vitals = EngineVitals.from(state.telemetry),
            )
            else -> DashboardReadings(status = state.dashboardStatus())
        }
    }
}

private fun SignalReading.valueOrNull(): Double? = (this as? SignalReading.Sample)?.value

/** Short connection status for the dashboard; null while readings are live. */
private fun DiagnosticState.dashboardStatus(): String? = when (this) {
    DiagnosticState.Disconnected -> "Disconnected"
    DiagnosticState.ConnectingBluetooth -> "Connecting"
    DiagnosticState.WaitingForAdapter -> "Waiting for adapter"
    DiagnosticState.WaitingForEcu -> "Waiting for ECU"
    is DiagnosticState.Reconnecting -> "Link lost · Reconnecting ($attempt)…"
    is DiagnosticState.Failed -> "Session failed"
    is DiagnosticState.Live -> null
}
