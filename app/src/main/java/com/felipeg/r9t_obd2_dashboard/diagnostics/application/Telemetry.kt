package com.felipeg.r9t_obd2_dashboard.diagnostics.application

/** The current session's additional Mode 01 readings, indexed by numeric PID. */
data class TelemetrySnapshot(
    val advertisedPids: Set<Int> = emptySet(),
    val readings: Map<Int, TelemetryReading> = emptyMap(),
)

data class TelemetryReading(
    val label: String,
    val receivedAtMillis: Long,
    val payload: String?,
    val measurements: List<TelemetryMeasurement> = emptyList(),
    val status: TelemetryStatus,
)

data class TelemetryMeasurement(val label: String, val value: Double, val unit: String)

enum class TelemetryStatus { NUMERIC, RAW, NO_DATA, INVALID_RESPONSE }

/**
 * Mode 01 PIDs a rider watches while riding. Sessions refresh these ahead of the
 * diagnostic-only readings, and dashboards read them from [TelemetrySnapshot.readings].
 */
object EngineVitalPids {
    const val ENGINE_TEMPERATURE = 0x05
    const val INTAKE_AIR_TEMPERATURE = 0x0F
    const val MODULE_VOLTAGE = 0x42
    const val OIL_TEMPERATURE = 0x5C

    val all = setOf(ENGINE_TEMPERATURE, INTAKE_AIR_TEMPERATURE, MODULE_VOLTAGE, OIL_TEMPERATURE)
}

/** The first numeric measurement for [pid], or null when it has not been read or did not decode. */
fun TelemetrySnapshot.numericValue(pid: Int): Double? =
    readings[pid]?.takeIf { it.status == TelemetryStatus.NUMERIC }?.measurements?.firstOrNull()?.value
