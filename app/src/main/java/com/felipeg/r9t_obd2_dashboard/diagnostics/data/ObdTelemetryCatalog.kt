package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryMeasurement
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryReading
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryStatus

internal data class ObdTelemetryPid(
    val id: Int,
    val label: String,
    val lengths: IntRange,
    val decode: (List<Int>) -> List<TelemetryMeasurement>,
) {
    val command: String get() = ObdPids.request(id)
}

internal object ObdTelemetryCatalog {
    // Preserve bitmaps and enums as raw bytes until their individual fields are needed.
    val pids = listOf(
        raw(0x01, "Monitor status since DTCs cleared", 4..4),
        raw(0x03, "Fuel system status", 2..2),
        percentage(0x04, "Calculated engine load"),
        temperature(0x05, "Engine coolant temperature"),
        trim(0x06, "Short-term fuel trim bank 1"),
        trim(0x07, "Long-term fuel trim bank 1"),
        trim(0x08, "Short-term fuel trim bank 2"),
        trim(0x09, "Long-term fuel trim bank 2"),
        scalar(0x0A, "Fuel pressure", "kPa") { it[0] * 3.0 },
        scalar(0x0E, "Ignition timing advance", "°") { it[0] / 2.0 - 64 },
        temperature(0x0F, "Intake air temperature"),
        percentage(0x11, "Throttle position"),
        raw(0x13, "Oxygen sensor locations", 1..1),
        ObdTelemetryPid(0x15, "Oxygen sensor 2", 2..2) { bytes ->
            buildList {
                add(TelemetryMeasurement("Voltage", bytes[0] / 200.0, "V"))
                if (bytes[1] != 0xFF) {
                    add(TelemetryMeasurement("Short-term fuel trim", fuelTrim(bytes[1]), "%"))
                }
            }
        },
        raw(0x1C, "OBD standard", 1..1),
        word(0x1F, "Engine run time", "s"),
        word(0x21, "Distance with malfunction indicator on", "km"),
        percentage(0x2E, "Commanded evaporative purge"),
        scalar(0x30, "Warm-ups since DTCs cleared", "count") { it[0].toDouble() },
        word(0x31, "Distance since DTCs cleared", "km"),
        scalar(0x33, "Barometric pressure", "kPa") { it[0].toDouble() },
        wideband(0x34, "Wideband oxygen sensor 1"),
        wideband(0x38, "Wideband oxygen sensor 5"),
        raw(0x41, "Monitor status this drive cycle", 4..4),
        word(0x42, "Control module voltage", "V", .001),
        word(0x43, "Absolute engine load", "%", 100.0 / 255),
        word(0x44, "Commanded equivalence ratio", "ratio", 1.0 / 32768),
        percentage(0x45, "Relative throttle position"),
        percentage(0x47, "Absolute throttle position B"),
        percentage(0x48, "Absolute throttle position C"),
        percentage(0x49, "Accelerator pedal position D"),
        percentage(0x4A, "Accelerator pedal position E"),
        word(0x4D, "Time with malfunction indicator on", "min"),
        word(0x4E, "Time since DTCs cleared", "min"),
        trim(0x56, "Long-term secondary oxygen sensor trim bank 1"),
        trim(0x58, "Long-term secondary oxygen sensor trim bank 2"),
        percentage(0x5A, "Relative accelerator pedal position"),
        temperature(0x5C, "Engine oil temperature"),
        percentage(0x8D, "Throttle position G"),
    )

    fun read(pid: ObdTelemetryPid, response: String, receivedAtMillis: Long): TelemetryReading {
        val bytes = ObdPidDecoder.telemetryPayload(response, pid)
        if (bytes == null) {
            return TelemetryReading(
                label = pid.label,
                receivedAtMillis = receivedAtMillis,
                payload = null,
                status = if (ElmResponse(response).isNoData) TelemetryStatus.NO_DATA
                else TelemetryStatus.INVALID_RESPONSE,
            )
        }

        val measurements = pid.decode(bytes)
        return TelemetryReading(
            label = pid.label,
            receivedAtMillis = receivedAtMillis,
            payload = bytes.joinToString(" ", transform = ObdPids::hex),
            measurements = measurements,
            status = if (measurements.isEmpty()) TelemetryStatus.RAW else TelemetryStatus.NUMERIC,
        )
    }

    private fun raw(id: Int, label: String, lengths: IntRange) =
        ObdTelemetryPid(id, label, lengths) { emptyList() }

    private fun scalar(
        id: Int,
        label: String,
        unit: String,
        lengths: IntRange = 1..1,
        decode: (List<Int>) -> Double,
    ) = ObdTelemetryPid(id, label, lengths) { listOf(TelemetryMeasurement(label, decode(it), unit)) }

    private fun temperature(id: Int, label: String) = scalar(id, label, "°C") { it[0] - 40.0 }

    private fun percentage(id: Int, label: String) = scalar(id, label, "%") { it[0] * 100.0 / 255 }

    private fun trim(id: Int, label: String) = scalar(id, label, "%", 1..2) { fuelTrim(it[0]) }

    private fun word(id: Int, label: String, unit: String, multiplier: Double = 1.0) =
        scalar(id, label, unit, 2..2) { wordValue(it, 0) * multiplier }

    private fun wideband(id: Int, label: String) = ObdTelemetryPid(id, label, 4..4) {
        listOf(
            TelemetryMeasurement("Equivalence ratio", wordValue(it, 0) / 32768.0, "ratio"),
            TelemetryMeasurement("Current", wordValue(it, 2) / 256.0 - 128, "mA"),
        )
    }

    private fun wordValue(bytes: List<Int>, offset: Int): Int = bytes[offset] * 256 + bytes[offset + 1]

    private fun fuelTrim(byte: Int): Double = (byte - 128) * 100.0 / 128
}
