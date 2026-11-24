package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdTelemetryCatalogTest {
    @Test
    fun convertsTemperaturesAndVoltageAndRetainsPayloads() {
        val temperature = read(0x05, "41 05 7B\r>")
        assertEquals(83.0, temperature.measurements.single().value, 0.0)
        assertEquals("°C", temperature.measurements.single().unit)
        assertEquals("7B", temperature.payload)
        assertEquals(15L, temperature.receivedAtMillis)
        assertEquals(14.125, read(0x42, "41 42 37 2D\r>").measurements.single().value, 0.0001)
    }

    @Test
    fun distinguishesValidZeroRawNoDataAndWrongPid() {
        val zero = read(0x31, "41 31 00 00\r>")
        assertEquals(TelemetryStatus.NUMERIC, zero.status)
        assertEquals(0.0, zero.measurements.single().value, 0.0)
        val status = read(0x01, "41 01 00 07 E1 00\r>")
        assertEquals(TelemetryStatus.RAW, status.status)
        assertEquals("00 07 E1 00", status.payload)
        assertTrue(status.measurements.isEmpty())
        assertEquals(TelemetryStatus.NO_DATA, read(0x05, "NO DATA\r>").status)
        assertEquals(TelemetryStatus.INVALID_RESPONSE, read(0x05, "41 0F 7B\r>").status)
    }

    @Test
    fun decodesBothWidebandFieldsAndSupportsSingleByteTrimResponses() {
        val wideband = read(0x34, "41 34 80 00 80 00\r>")
        assertEquals(listOf(1.0, 0.0), wideband.measurements.map { it.value })
        assertEquals(0.0, read(0x56, "41 56 80\r>").measurements.single().value, 0.0)
    }

    private fun read(id: Int, response: String) =
        ObdTelemetryCatalog.read(ObdTelemetryCatalog.pids.single { it.id == id }, response, 15)
}
