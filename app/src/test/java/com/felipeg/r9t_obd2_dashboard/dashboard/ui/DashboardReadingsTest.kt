package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.EngineVitalPids
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.GearPosition
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryMeasurement
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryReading
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetrySnapshot
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardReadingsTest {
    @Test
    fun aLiveSessionGivesItsSamplesAndNoStatus() {
        val coolant = TelemetryReading("Coolant", 0, "9B", listOf(TelemetryMeasurement("Coolant", 115.0, "°C")), TelemetryStatus.NUMERIC)
        val live = DiagnosticState.Live(
            protocol = "CAN",
            engineRpm = SignalReading.Sample(1_726.0, 10),
            speedKmh = SignalReading.Sample(0.0, 10),
            gear = GearPosition.Neutral,
            odometerKm = SignalReading.Sample(12_480.5, 10),
            telemetry = TelemetrySnapshot(readings = mapOf(EngineVitalPids.ENGINE_TEMPERATURE to coolant)),
        )

        assertEquals(
            DashboardReadings(
                rpm = 1_726.0,
                speedKmh = 0.0,
                gear = GearPosition.Neutral,
                odometerKm = 12_480.5,
                vitals = EngineVitals(engineTemperatureC = 115.0),
                status = null,
            ),
            DashboardReadings.from(live),
        )
    }

    @Test
    fun missingSignalsStayMissingInsteadOfZero() {
        val live = DiagnosticState.Live("CAN", SignalReading.Unavailable, SignalReading.Unavailable)

        assertEquals(DashboardReadings(), DashboardReadings.from(live))
    }

    @Test
    fun aSessionThatIsNotLiveShowsOnlyItsStatus() {
        mapOf(
            DiagnosticState.Disconnected to "Disconnected",
            DiagnosticState.ConnectingBluetooth to "Connecting",
            DiagnosticState.WaitingForAdapter to "Waiting for adapter",
            DiagnosticState.WaitingForEcu to "Waiting for ECU",
            DiagnosticState.Reconnecting(2, DiagnosticError.CONNECTION_LOST) to "Link lost · Reconnecting (2)…",
            DiagnosticState.Failed(DiagnosticStage.ECU, DiagnosticError.TIMEOUT) to "Session failed",
        ).forEach { (state, status) ->
            assertEquals(DashboardReadings(status = status), DashboardReadings.from(state))
        }
    }
}
