package com.felipeg.r9t_obd2_dashboard.dashboard.ui.sport

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.EngineVitals
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.ReadingSpring
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.TachometerScale
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.VitalsFormat
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftBarGauge
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftBars
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftCaptionValue
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftColors
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftGaugeRange
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftScreenFrame
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftTypography
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.EngineVitalPids
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.GearPosition
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryMeasurement
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryReading
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetrySnapshot
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryStatus
import kotlin.math.roundToInt

/**
 * Temperature scale for the side gauges. The reference bike idled at 113–124 °C engine and
 * 96–105 °C oil; the red bars are a visual cue, not the manufacturer's warning threshold.
 */
private val TemperatureRange = TftGaugeRange(min = 40.0, max = 140.0, warnFrom = 130.0, bars = 12)
private val GaugeBarsHeight = 84.dp
private val MaxGaugeWidth = 132.dp
private val MinGaugeWidth = 72.dp
private val GearBoxWidth = 40.dp
private val GearBoxHeight = 46.dp

/**
 * Round sport layout inside the TFT frame: battery, connection status and gear (when the source
 * provides one) in the top bar; the tachometer with the speed in its center and engine and oil
 * temperature at its sides; intake air and the clock in the bottom bar, whose center the host
 * keeps for the display selector.
 */
@Composable
internal fun SportDashboardPanel(
    live: DiagnosticState.Live?,
    time: String,
    status: String?,
    modifier: Modifier = Modifier,
) {
    val rpm = (live?.engineRpm as? SignalReading.Sample)?.value
    val speed = (live?.speedKmh as? SignalReading.Sample)?.value
    val vitals = live?.let { EngineVitals.from(it.telemetry) } ?: EngineVitals.Unavailable
    val animatedRpm by animateFloatAsState(TachometerScale.fromRpm(rpm), ReadingSpring, label = "sport rpm")
    val animatedSpeed by animateFloatAsState((speed ?: 0.0).toFloat(), ReadingSpring, label = "sport speed")

    TftScreenFrame(
        modifier,
        topStart = { VitalReadout("Batt", VitalsFormat.volts(vitals.batteryVolts), vitals.batteryVolts) },
        topCenter = { status?.let { StatusText(it) } },
        topEnd = { live?.gear?.let { SportGearBox(it, Modifier.size(GearBoxWidth, GearBoxHeight)) } },
        bottomStart = {
            VitalReadout("Air", VitalsFormat.signedTemperature(vitals.intakeAirC), vitals.intakeAirC)
        },
        bottomEnd = { ClockText(time) },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth <= maxHeight) {
                Text(
                    "Rotate your phone to view the dashboard.",
                    color = TftColors.White,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                return@BoxWithConstraints
            }
            val layout = SportDialLayout.fit(maxWidth, maxHeight)
            SportTachometer(rpm?.let { animatedRpm }, layout, Modifier.fillMaxSize())
            SportSpeedReadout(speed?.let { animatedSpeed.roundToInt() }, layout)
            SideGauges(vitals, layout)
        }
    }
}

/** Engine temperature on the left and oil temperature on the right, beside the dial. */
@Composable
private fun BoxScope.SideGauges(vitals: EngineVitals, layout: SportDialLayout) {
    val gaugeWidth = min(layout.centerX - layout.radius - TftBars.SideMargin - 16.dp, MaxGaugeWidth)
    if (gaugeWidth < MinGaugeWidth) return
    TftBarGauge(
        label = "Engine",
        value = vitals.engineTemperatureC,
        valueText = VitalsFormat.temperature(vitals.engineTemperatureC),
        range = TemperatureRange,
        scaleOnStart = true,
        modifier = Modifier.align(Alignment.CenterStart).padding(start = TftBars.SideMargin).width(gaugeWidth),
        barsHeight = GaugeBarsHeight,
    )
    TftBarGauge(
        label = "Oil",
        value = vitals.oilTemperatureC,
        valueText = VitalsFormat.temperature(vitals.oilTemperatureC),
        range = TemperatureRange,
        scaleOnStart = false,
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = TftBars.SideMargin).width(gaugeWidth),
        barsHeight = GaugeBarsHeight,
    )
}

@Composable
private fun VitalReadout(caption: String, value: String, reading: Double?) =
    TftCaptionValue(caption, value, available = reading != null)

/** Connection status in the top bar, where the original shows the riding mode. */
@Composable
private fun StatusText(status: String) = Text(
    status.uppercase(),
    color = TftColors.Cyan,
    style = TftTypography.style(18.dp, FontWeight.Medium, italic = false),
    maxLines = 1,
)

@Composable
private fun ClockText(time: String) = Text(
    time,
    color = TftColors.White,
    style = TftTypography.style(22.dp, FontWeight.Medium, italic = false),
)

@Preview(name = "Sport · cruising", widthDp = 780, heightDp = 360)
@Composable
private fun SportCruisingPreview() = SportPreview(4_250.0, 87.0, GearPosition.Forward(4))

@Preview(name = "Sport · limit zone", widthDp = 780, heightDp = 360)
@Composable
private fun SportLimitPreview() = SportPreview(9_200.0, 142.0, GearPosition.Forward(3), engine = 133.0)

@Preview(name = "Sport · idle, no gear", widthDp = 780, heightDp = 360)
@Composable
private fun SportIdlePreview() = SportPreview(1_150.0, 0.0, gear = null)

@Preview(name = "Sport · disconnected", widthDp = 780, heightDp = 360)
@Composable
private fun SportDisconnectedPreview() = SportDashboardPanel(null, "09:10", "Disconnected")

@Composable
private fun SportPreview(rpm: Double, speed: Double, gear: GearPosition?, engine: Double = 113.0) =
    SportDashboardPanel(
        DiagnosticState.Live(
            "CAN", SignalReading.Sample(rpm, 0), SignalReading.Sample(speed, 0), gear,
            telemetry = previewTelemetry(engine),
        ),
        "09:10", null,
    )

private fun previewTelemetry(engine: Double) = TelemetrySnapshot(
    readings = mapOf(
        EngineVitalPids.ENGINE_TEMPERATURE to previewReading(engine, "°C"),
        EngineVitalPids.OIL_TEMPERATURE to previewReading(98.0, "°C"),
        EngineVitalPids.MODULE_VOLTAGE to previewReading(14.2, "V"),
        EngineVitalPids.INTAKE_AIR_TEMPERATURE to previewReading(24.0, "°C"),
    ),
)

private fun previewReading(value: Double, unit: String) = TelemetryReading(
    label = "Preview", receivedAtMillis = 0, payload = null,
    measurements = listOf(TelemetryMeasurement("Preview", value, unit)), status = TelemetryStatus.NUMERIC,
)
