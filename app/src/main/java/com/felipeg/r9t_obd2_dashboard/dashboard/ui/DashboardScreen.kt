package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.GearPosition
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.rememberDashboardClock
import java.text.NumberFormat
import kotlin.math.roundToInt

private val Background = Color.Black
private val GearSurface = Color(0xFF24383D)
private val Outline = Color(0xFF344955)
private val Muted = Color(0xFFB7C6CB)
private val White = Color(0xFFF5F9F7)
private val GearAccent = Color(0xFFFFD23F)
private val NeutralAccent = Color(0xFFA6EF72)

@Composable
internal fun DashboardScreen(
    state: DiagnosticState,
    onDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    sourceLabel: String = "Simulation",
) {
    RideMode()
    val time = rememberDashboardClock()
    val status = when (state) {
        DiagnosticState.Disconnected -> "Disconnected"
        DiagnosticState.ConnectingBluetooth -> "Connecting"
        DiagnosticState.WaitingForAdapter -> "Waiting for adapter"
        DiagnosticState.WaitingForEcu -> "Waiting for ECU"
        is DiagnosticState.Reconnecting -> "Link lost · Reconnecting (${state.attempt})…"
        is DiagnosticState.Failed -> "Session failed"
        is DiagnosticState.Live -> null
    }
    DashboardPanel(state as? DiagnosticState.Live, time, status, onDiagnostics, modifier, sourceLabel)
}

@Composable
private fun DashboardPanel(
    live: DiagnosticState.Live?,
    time: String,
    status: String?,
    onDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    sourceLabel: String = "Simulation",
) {
    val rpm = live?.engineRpm.valueOrNull()
    val speed = live?.speedKmh.valueOrNull()
    val distance = live?.odometerKm.valueOrNull()
    val gear = when (val position = live?.gear) {
        GearPosition.Neutral -> "N"
        is GearPosition.Forward -> position.number.toString()
        null -> "—"
    }
    val odometer = distance?.let { NumberFormat.getIntegerInstance().format(it) } ?: "—"
    BoxWithConstraints(modifier.fillMaxSize().background(Background)) {
        if (maxWidth <= maxHeight) {
            Column(
                Modifier.align(Alignment.Center).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Rotate your phone to view the dashboard.", color = White)
                TextButton(onClick = onDiagnostics) { Text("Diagnostics", color = Muted) }
            }
            return@BoxWithConstraints
        }
        val instrumentHeight = (maxHeight - 100.dp).coerceIn(170.dp, 430.dp)
        val frame = RoundedCornerShape(10.dp)
        Box(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.padding(8.dp)
                    .widthIn(max = 960.dp)
                    .fillMaxWidth()
                    .background(Background, frame)
                    .border(1.dp, Outline, frame)
                    .padding(horizontal = 18.dp, vertical = 4.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ODOMETER  $odometer km", color = Muted,
                        style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
                    DashboardMenu(sourceLabel, onDiagnostics)
                }
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(1.dp)
                        .background(Brush.horizontalGradient(listOf(Outline, GearAccent.copy(alpha = .72f), Outline))),
                )
                BoxWithConstraints(Modifier.fillMaxWidth().height(instrumentHeight)) {
                    val digitSize = minOf(maxHeight.value * .40f, maxWidth.value * .145f).sp
                    val digitWidth = maxWidth * .29f
                    val unitWidth = maxWidth * .105f
                    val gearWidth = (maxWidth * .17f).coerceAtMost(116.dp)
                    val gearInset = maxWidth * .07f
                    val gearHeight = (maxHeight * .37f).coerceAtMost(100.dp)
                    RpmGauge(rpm, Modifier.fillMaxSize())
                    Box(
                        Modifier.fillMaxWidth()
                            .offset(y = maxHeight * .40f)
                            .height(maxHeight * .48f),
                    ) {
                        Row(Modifier.align(Alignment.Center)) {
                            Spacer(Modifier.width(unitWidth))
                            Text(
                                speed?.roundToInt()?.toString() ?: "—",
                                modifier = Modifier.widthIn(max = digitWidth).alignByBaseline().padding(end = 5.dp),
                                color = White,
                                textAlign = TextAlign.Center,
                                fontSize = digitSize,
                                lineHeight = digitSize,
                                fontWeight = FontWeight.Black,
                                fontStyle = FontStyle.Italic,
                                maxLines = 1,
                            )
                            Text(
                                "km/h",
                                modifier = Modifier.width(unitWidth).alignByBaseline(),
                                color = Muted,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        }
                        val gearShape = RoundedCornerShape(5.dp)
                        Box(
                            Modifier.align(Alignment.CenterEnd)
                                .padding(end = gearInset)
                                .width(gearWidth)
                                .height(gearHeight)
                                .background(GearSurface, gearShape)
                                .border(1.dp, Outline.copy(alpha = .50f), gearShape)
                                .semantics {
                                    contentDescription = if (gear == "—") "Gear unavailable" else "Gear $gear"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                gear,
                                color = if (live?.gear == GearPosition.Neutral) NeutralAccent else GearAccent,
                                fontSize = minOf(gearHeight.value * .65f, gearWidth.value * .80f).sp,
                                fontWeight = FontWeight.Black,
                                fontStyle = FontStyle.Italic,
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(status.orEmpty(), color = Muted, style = MaterialTheme.typography.labelMedium)
                    Box(Modifier.weight(1f))
                    Text(time, color = Muted,
                        fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun DashboardMenu(sourceLabel: String, onDiagnostics: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = "Dashboard menu" },
        ) {
            Text("⋯", color = Muted, style = MaterialTheme.typography.titleMedium)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(sourceLabel) }, onClick = {}, enabled = false)
            DropdownMenuItem(text = { Text("Diagnostics") }, onClick = {
                expanded = false
                onDiagnostics()
            })
        }
    }
}

/**
 * While the dashboard is shown, keeps the screen on and hides the system bars.
 * A swipe from the edge reveals the bars temporarily. Both are restored on exit.
 */
@Composable
private fun RideMode() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        view.keepScreenOn = true
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun SignalReading?.valueOrNull(): Double? = (this as? SignalReading.Sample)?.value

@Preview(name = "Landscape · live", widthDp = 780, heightDp = 360)
@Composable
private fun DashboardLivePreview() {
    DashboardPanel(
        DiagnosticState.Live("Simulated CAN", SignalReading.Sample(8_450.0, 0),
            SignalReading.Sample(94.0, 0), GearPosition.Forward(4), SignalReading.Sample(12_480.0, 0)),
        "5:36 PM", null, {},
    )
}

@Preview(name = "Landscape · partial red zone", widthDp = 780, heightDp = 360)
@Composable
private fun DashboardPartialRedZonePreview() {
    DashboardPanel(
        DiagnosticState.Live("Simulated CAN", SignalReading.Sample(8_900.0, 0),
            SignalReading.Sample(94.0, 0), GearPosition.Forward(4), SignalReading.Sample(12_480.0, 0)),
        "5:36 PM", null, {},
    )
}

@Preview(name = "Landscape · red zone", widthDp = 780, heightDp = 360)
@Composable
private fun DashboardRedZonePreview() {
    DashboardPanel(
        DiagnosticState.Live("Simulated CAN", SignalReading.Sample(9_000.0, 0),
            SignalReading.Sample(94.0, 0), GearPosition.Forward(4), SignalReading.Sample(12_480.0, 0)),
        "5:36 PM", null, {},
    )
}

@Preview(name = "Landscape · zero", widthDp = 780, heightDp = 360)
@Composable
private fun DashboardZeroPreview() {
    DashboardPanel(
        DiagnosticState.Live("Simulated CAN", SignalReading.Sample(1_100.0, 0),
            SignalReading.Sample(0.0, 0), GearPosition.Neutral, SignalReading.Sample(12_480.0, 0)),
        "5:36 PM", null, {},
    )
}

@Preview(name = "Landscape · unavailable", widthDp = 780, heightDp = 360)
@Composable
private fun DashboardUnavailablePreview() {
    DashboardPanel(null, "5:36 PM", "Disconnected", {})
}
