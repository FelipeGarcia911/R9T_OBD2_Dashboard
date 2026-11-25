package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.EngineVitalPids
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryMeasurement
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryReading
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetrySnapshot
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class EngineVitalsTest {
    private fun reading(value: Double, status: TelemetryStatus = TelemetryStatus.NUMERIC) =
        TelemetryReading("x", 0, null, listOf(TelemetryMeasurement("x", value, "u")), status)

    @Test
    fun readsVitalsFromTheirPidsAndIgnoresUndecodedReadings() {
        val vitals = EngineVitals.from(TelemetrySnapshot(readings = mapOf(
            EngineVitalPids.ENGINE_TEMPERATURE to reading(113.0),
            EngineVitalPids.OIL_TEMPERATURE to reading(96.0),
            EngineVitalPids.MODULE_VOLTAGE to reading(14.411),
            EngineVitalPids.INTAKE_AIR_TEMPERATURE to reading(33.0, TelemetryStatus.INVALID_RESPONSE),
        )))
        assertEquals(EngineVitals(113.0, 96.0, 14.411, null), vitals)
    }

    @Test
    fun formatsValuesLikeTheTftAndShowsDashesWhenMissing() {
        assertEquals("113 °C", VitalsFormat.temperature(113.4))
        assertEquals("-- °C", VitalsFormat.temperature(null))
        assertEquals("+33 °C", VitalsFormat.signedTemperature(33.0))
        assertEquals("-3 °C", VitalsFormat.signedTemperature(-3.2))
        assertEquals("0 °C", VitalsFormat.signedTemperature(0.2))
        assertEquals("14.4 V", VitalsFormat.volts(14.411))
        assertEquals("-- V", VitalsFormat.volts(null))
    }
}
