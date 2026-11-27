package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import android.app.UiAutomation
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.felipeg.r9t_obd2_dashboard.dashboard.shiftlight.ShiftLightSettings
import com.felipeg.r9t_obd2_dashboard.dashboard.shiftlight.ShiftLightSettingsStore
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.link.DashboardConnection
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.EngineVitalPids
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.GearPosition
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.SignalReading
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryMeasurement
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryReading
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetrySnapshot
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.TelemetryStatus
import kotlin.math.PI
import kotlin.math.sin

/** Readings arrive every ~65 ms with the single-reply requests measured on 2026-11-25. */
internal const val READING_PERIOD_MILLIS = 66L

/**
 * Shows [layout] full screen in landscape with real frames, feeding it a simulated rev every
 * [READING_PERIOD_MILLIS] for [durationMillis], then closes it. [onActivity] runs once the
 * dashboard is set, for attaching frame listeners; [onTick] gets the elapsed time of each reading.
 */
internal fun runDashboard(
    layout: DashboardLayout,
    durationMillis: Long,
    probe: RenderProbe = RenderProbe { },
    onActivity: (ComponentActivity) -> Unit = {},
    onTick: (elapsedMillis: Long) -> Unit = {},
) {
    val session = mutableStateOf<DiagnosticState>(DiagnosticState.Disconnected)
    showDashboard(layout, probe, onActivity, state = { session.value }) { scenario ->
        val startedAt = System.nanoTime()
        while (true) {
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
            if (elapsedMillis >= durationMillis) break
            onTick(elapsedMillis)
            scenario.onActivity { session.value = ride(elapsedMillis) }
            Thread.sleep(READING_PERIOD_MILLIS)
        }
    }
}

/**
 * Shows [layout] full screen in landscape with real frames, reading its session from [state],
 * runs [drive] while it is shown, then closes it.
 */
internal fun showDashboard(
    layout: DashboardLayout,
    probe: RenderProbe = RenderProbe { },
    onActivity: (ComponentActivity) -> Unit = {},
    state: @Composable () -> DiagnosticState,
    drive: (ActivityScenario<ComponentActivity>) -> Unit,
) {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    automation.setRotation(UiAutomation.ROTATION_FREEZE_90)
    try {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    CompositionLocalProvider(LocalRenderProbe provides probe) {
                        DashboardScreen(
                            state = state(),
                            sourceLabel = "Test",
                            connection = DashboardConnection(connect = {}, disconnect = {}),
                            layoutStore = FixedLayoutStore(layout),
                            shiftLightStore = FixedShiftLightStore,
                        )
                    }
                }
                onActivity(activity)
            }
            drive(scenario)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    } finally {
        automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
    }
}

/** A rev from idle to past the shift point and back every four seconds, speed rising with it. */
internal fun ride(elapsedMillis: Long): DiagnosticState.Live {
    val phase = (elapsedMillis % 4_000) / 4_000.0
    return DiagnosticState.Live(
        protocol = "CAN",
        engineRpm = SignalReading.Sample(1_200 + 6_800 * sin(phase * PI), elapsedMillis),
        speedKmh = SignalReading.Sample(40 + 60 * sin(phase * PI), elapsedMillis),
        gear = GearPosition.Forward(3),
        odometerKm = SignalReading.Sample(12_480.0, elapsedMillis),
        telemetry = Vitals,
    )
}

private class FixedLayoutStore(private val layout: DashboardLayout) : DashboardLayoutStore {
    override fun load() = layout
    override fun save(layout: DashboardLayout) = Unit
}

private object FixedShiftLightStore : ShiftLightSettingsStore {
    override fun load() = ShiftLightSettings()
    override fun save(settings: ShiftLightSettings) = Unit
}

private val Vitals = TelemetrySnapshot(
    readings = mapOf(
        EngineVitalPids.ENGINE_TEMPERATURE to vital(115.0, "°C"),
        EngineVitalPids.OIL_TEMPERATURE to vital(98.0, "°C"),
        EngineVitalPids.MODULE_VOLTAGE to vital(14.3, "V"),
        EngineVitalPids.INTAKE_AIR_TEMPERATURE to vital(22.0, "°C"),
    ),
)

private fun vital(value: Double, unit: String) =
    TelemetryReading("Vital", 0, null, listOf(TelemetryMeasurement("Vital", value, unit)), TelemetryStatus.NUMERIC)
