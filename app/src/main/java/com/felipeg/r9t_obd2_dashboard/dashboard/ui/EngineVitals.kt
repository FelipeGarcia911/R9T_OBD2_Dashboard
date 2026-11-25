package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.EngineVitalPids
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetrySnapshot
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.numericValue
import java.util.Locale
import kotlin.math.roundToInt

/** Engine readings a dashboard shows next to RPM and speed. Null means not read yet. */
internal data class EngineVitals(
    val engineTemperatureC: Double? = null,
    val oilTemperatureC: Double? = null,
    val batteryVolts: Double? = null,
    val intakeAirC: Double? = null,
) {
    companion object {
        val Unavailable = EngineVitals()

        fun from(snapshot: TelemetrySnapshot) = EngineVitals(
            engineTemperatureC = snapshot.numericValue(EngineVitalPids.ENGINE_TEMPERATURE),
            oilTemperatureC = snapshot.numericValue(EngineVitalPids.OIL_TEMPERATURE),
            batteryVolts = snapshot.numericValue(EngineVitalPids.MODULE_VOLTAGE),
            intakeAirC = snapshot.numericValue(EngineVitalPids.INTAKE_AIR_TEMPERATURE),
        )
    }
}

/** Formats readings the way the TFT shows them; a missing value reads as two dashes. */
internal object VitalsFormat {
    private const val MISSING = "--"

    fun temperature(celsius: Double?): String = celsius?.let { "${it.roundToInt()} °C" } ?: "$MISSING °C"

    /** Signed, as the TFT shows outside temperature: +24 °C, -3 °C. */
    fun signedTemperature(celsius: Double?): String = celsius?.roundToInt()
        ?.let { "${if (it > 0) "+" else ""}$it °C" } ?: "$MISSING °C"

    fun volts(volts: Double?): String = volts?.let { String.format(Locale.US, "%.1f V", it) } ?: "$MISSING V"
}
