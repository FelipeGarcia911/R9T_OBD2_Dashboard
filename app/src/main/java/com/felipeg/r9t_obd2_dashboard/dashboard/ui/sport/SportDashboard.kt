package com.felipeg.r9t_obd2_dashboard.dashboard.ui.sport

import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftColors
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftTypography
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.ReadingSpring
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.TachometerScale
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.GearPosition
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading
import kotlin.math.roundToInt

/**
 * Round sport layout: semicircular tachometer with the speed in its center and the gear,
 * when the data source provides it, in the top-right corner. [toolbar] is supplied by the host.
 */
@Composable
internal fun SportDashboardPanel(
    live: DiagnosticState.Live?,
    time: String,
    status: String?,
    toolbar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rpm = (live?.engineRpm as? SignalReading.Sample)?.value
    val speed = (live?.speedKmh as? SignalReading.Sample)?.value
    val animatedRpm by animateFloatAsState(TachometerScale.fromRpm(rpm), ReadingSpring, label = "sport rpm")
    val animatedSpeed by animateFloatAsState((speed ?: 0.0).toFloat(), ReadingSpring, label = "sport speed")

    BoxWithConstraints(
        modifier.fillMaxSize().background(Brush.radialGradient(listOf(TftColors.GroundCenter, TftColors.GroundEdge))),
    ) {
        if (maxWidth <= maxHeight) {
            RotatePrompt(toolbar, Modifier.align(Alignment.Center))
            return@BoxWithConstraints
        }
        val layout = SportDialLayout.fit(maxWidth, maxHeight)
        SportTachometer(rpm?.let { animatedRpm }, layout, Modifier.fillMaxSize())
        SportSpeedReadout(speed?.let { animatedSpeed.roundToInt() }, layout)
        live?.gear?.let { gear ->
            SportGearBox(gear, layout.radius, Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 16.dp))
        }
        Box(Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 4.dp)) { toolbar() }
        StatusRow(status, time, layout, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun RotatePrompt(toolbar: @Composable () -> Unit, modifier: Modifier) {
    Column(modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Rotate your phone to view the dashboard.", color = TftColors.White)
        toolbar()
    }
}

/** Connection status on the left and the local time in cyan on the right. */
@Composable
private fun StatusRow(status: String?, time: String, layout: SportDialLayout, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            status.orEmpty(),
            color = TftColors.White,
            style = TftTypography.style(layout.radius * .075f, FontWeight.Medium, italic = false),
            modifier = Modifier.weight(1f),
        )
        Text(
            time,
            color = TftColors.Cyan,
            style = TftTypography.style(layout.radius * .085f, FontWeight.Medium, italic = false),
        )
    }
}

@Preview(name = "Sport · cruising", widthDp = 780, heightDp = 360)
@Composable
private fun SportCruisingPreview() = SportPreview(4_250.0, 87.0, GearPosition.Forward(4))

@Preview(name = "Sport · limit zone", widthDp = 780, heightDp = 360)
@Composable
private fun SportLimitPreview() = SportPreview(9_200.0, 142.0, GearPosition.Forward(3))

@Preview(name = "Sport · idle, no gear", widthDp = 780, heightDp = 360)
@Composable
private fun SportIdlePreview() = SportPreview(1_150.0, 0.0, gear = null)

@Preview(name = "Sport · disconnected", widthDp = 780, heightDp = 360)
@Composable
private fun SportDisconnectedPreview() = SportDashboardPanel(null, "09:10", "Disconnected", {})

@Composable
private fun SportPreview(rpm: Double, speed: Double, gear: GearPosition?) = SportDashboardPanel(
    DiagnosticState.Live("CAN", SignalReading.Sample(rpm, 0), SignalReading.Sample(speed, 0), gear),
    "09:10", null, {},
)
