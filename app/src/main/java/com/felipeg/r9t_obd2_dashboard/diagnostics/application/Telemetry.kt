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
