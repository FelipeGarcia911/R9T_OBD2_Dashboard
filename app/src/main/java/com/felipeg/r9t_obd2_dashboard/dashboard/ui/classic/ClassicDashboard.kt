package com.felipeg.r9t_obd2_dashboard.dashboard.ui.classic

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.DashboardReadings
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.LandscapeOnly
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.VitalsFormat
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.animateReadings
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftCaptionValue
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftClockText
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftColors
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftFrameStyle
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftModeText
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftScreenFrame
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.GearPosition
import java.text.NumberFormat

/**
 * Classic layout modeled on the TFT's plain riding view: battery, odometer and connection status
 * above an amber rule; the bar tachometer rising on the left and running across the top, the
 * speed in the middle and the gear (when the source provides one) on the right; outside air and
 * the clock in the bottom bar, whose center the host keeps for the display selector.
 */
@Composable
internal fun ClassicDashboardPanel(
    readings: DashboardReadings,
    time: String,
    modifier: Modifier = Modifier,
) {
    val odometer = readings.odometerKm
    val vitals = readings.vitals
    val animated = animateReadings(readings)

    TftScreenFrame(
        modifier,
        style = TftFrameStyle.Classic,
        topStart = {
            TftCaptionValue("Batt", VitalsFormat.volts(vitals.batteryVolts), available = vitals.batteryVolts != null)
        },
        topCenter = {
            TftCaptionValue("Total", odometer?.let { "${NumberFormat.getIntegerInstance().format(it)} km" } ?: "-- km",
                available = odometer != null)
        },
        topEnd = { readings.status?.let { TftModeText(it, TftColors.Amber) } },
        bottomStart = {
            TftCaptionValue("Air", VitalsFormat.signedTemperature(vitals.intakeAirC), available = vitals.intakeAirC != null)
        },
        bottomEnd = { TftClockText(time) },
    ) {
        LandscapeOnly(Modifier.padding(top = 6.dp)) {
            val layout = ClassicDialLayout.fit(maxWidth, maxHeight)
            ClassicTachometer(animated.tachometer, layout, Modifier.fillMaxSize())
            ClassicScaleCaption(layout)
            ClassicSpeedReadout(animated.speedKmh, layout)
            readings.gear?.let { ClassicGearIndicator(it, layout) }
            ClassicMenuChevron(layout)
        }
    }
}

@Preview(name = "Classic · cruising", widthDp = 780, heightDp = 360)
@Composable
private fun ClassicCruisingPreview() = ClassicPreview(5_450.0, 94.0, GearPosition.Forward(4))

@Preview(name = "Classic · limit zone", widthDp = 780, heightDp = 360)
@Composable
private fun ClassicLimitPreview() = ClassicPreview(9_100.0, 131.0, GearPosition.Forward(3))

@Preview(name = "Classic · idle in neutral", widthDp = 780, heightDp = 360)
@Composable
private fun ClassicIdlePreview() = ClassicPreview(1_150.0, 0.0, GearPosition.Neutral)

@Preview(name = "Classic · disconnected", widthDp = 780, heightDp = 360)
@Composable
private fun ClassicDisconnectedPreview() = ClassicDashboardPanel(DashboardReadings(status = "Disconnected"), "09:10")

@Composable
private fun ClassicPreview(rpm: Double, speed: Double, gear: GearPosition?) = ClassicDashboardPanel(
    DashboardReadings(rpm = rpm, speedKmh = speed, gear = gear, odometerKm = 12_480.0),
    "09:10",
)
