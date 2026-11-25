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
    val status: DashboardStatus? = null,
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

/** The connection state a display shows in place of readings. */
internal sealed interface DashboardStatus {
    data object Disconnected : DashboardStatus
    data object Connecting : DashboardStatus
    data object WaitingForAdapter : DashboardStatus
    data object WaitingForEcu : DashboardStatus
    data class Reconnecting(val attempt: Int) : DashboardStatus
    data object Failed : DashboardStatus
}

/** Null while readings are live. */
private fun DiagnosticState.dashboardStatus(): DashboardStatus? = when (this) {
    DiagnosticState.Disconnected -> DashboardStatus.Disconnected
    DiagnosticState.ConnectingBluetooth -> DashboardStatus.Connecting
    DiagnosticState.WaitingForAdapter -> DashboardStatus.WaitingForAdapter
    DiagnosticState.WaitingForEcu -> DashboardStatus.WaitingForEcu
    is DiagnosticState.Reconnecting -> DashboardStatus.Reconnecting(attempt)
    is DiagnosticState.Failed -> DashboardStatus.Failed
    is DiagnosticState.Live -> null
}
