package com.felipeg.r9t_obd2_dashboard.dashboard.ui.sport

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.DashboardReadings
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.DashboardStatus
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.text
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.EngineVitals
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.LandscapeOnly
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.VitalsFormat
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.animateReadings
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftBarGauge
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftBars
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftCaptionValue
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftClockText
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftColors
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftGaugeRange
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftModeText
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftScreenFrame
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.GearPosition
import androidx.compose.ui.res.stringResource
import com.felipeg.r9t_obd2_dashboard.R

/**
 * Temperature scale for the side gauges: 10 °C per row, so the long markers every fourth row
 * read 140, 100 and 60, with the unit on the bottom row. The reference bike idled at 113–124 °C
 * engine and 96–105 °C oil; the red row is a visual cue, not the manufacturer's threshold.
 */
private val TemperatureRange = TftGaugeRange(min = 20.0, max = 140.0, warnFrom = 130.0, bars = 12, majorEvery = 4)
private const val TEMPERATURE_UNIT = "°C"
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
    readings: DashboardReadings,
    time: String,
    modifier: Modifier = Modifier,
) {
    val vitals = readings.vitals
    val animated = animateReadings(readings)

    TftScreenFrame(
        modifier,
        topStart = { VitalReadout(stringResource(R.string.dashboard_battery), VitalsFormat.volts(vitals.batteryVolts), vitals.batteryVolts) },
        topCenter = { readings.status?.let { TftModeText(it.text(), TftColors.Cyan) } },
        topEnd = { readings.gear?.let { SportGearBox(it, Modifier.size(GearBoxWidth, GearBoxHeight)) } },
        bottomStart = {
            VitalReadout(stringResource(R.string.dashboard_air), VitalsFormat.signedTemperature(vitals.intakeAirC), vitals.intakeAirC)
        },
        bottomEnd = { TftClockText(time) },
    ) {
        LandscapeOnly(TftColors.White) {
            val layout = SportDialLayout.fit(maxWidth, maxHeight)
            SportTachometer(animated.tachometer, layout, Modifier.fillMaxSize())
            SportSpeedReadout(animated.speedKmh, layout)
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
        label = stringResource(R.string.dashboard_engine),
        value = vitals.engineTemperatureC,
        valueText = VitalsFormat.temperature(vitals.engineTemperatureC),
        range = TemperatureRange,
        scaleOnStart = true,
        unit = TEMPERATURE_UNIT,
        modifier = Modifier.align(Alignment.CenterStart).padding(start = TftBars.SideMargin).width(gaugeWidth),
        barsHeight = GaugeBarsHeight,
    )
    TftBarGauge(
        label = stringResource(R.string.dashboard_oil),
        value = vitals.oilTemperatureC,
        valueText = VitalsFormat.temperature(vitals.oilTemperatureC),
        range = TemperatureRange,
        scaleOnStart = false,
        unit = TEMPERATURE_UNIT,
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = TftBars.SideMargin).width(gaugeWidth),
        barsHeight = GaugeBarsHeight,
    )
}

@Composable
private fun VitalReadout(caption: String, value: String, reading: Double?) =
    TftCaptionValue(caption, value, available = reading != null)

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
private fun SportDisconnectedPreview() = SportDashboardPanel(DashboardReadings(status = DashboardStatus.Disconnected), "09:10")

@Composable
private fun SportPreview(rpm: Double, speed: Double, gear: GearPosition?, engine: Double = 113.0) =
    SportDashboardPanel(
        DashboardReadings(
            rpm = rpm, speedKmh = speed, gear = gear,
            vitals = EngineVitals(engineTemperatureC = engine, oilTemperatureC = 98.0, batteryVolts = 14.2, intakeAirC = 24.0),
        ),
        "09:10",
    )
