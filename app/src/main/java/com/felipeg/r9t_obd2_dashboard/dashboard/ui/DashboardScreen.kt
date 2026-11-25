package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.felipeg.r9t_obd2_dashboard.dashboard.shiftlight.ShiftLightSettingsStore
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.classic.ClassicDashboardPanel
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.shiftlight.ShiftLightOverlay
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.shiftlight.ShiftLightSettingsDialog
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.shiftlight.rememberShiftLightPhase
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.shiftlight.rememberShiftLightSettingsStore
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.sport.SportDashboardPanel
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.rememberDashboardClock

@Composable
internal fun DashboardScreen(
    state: DiagnosticState,
    onDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    sourceLabel: String = "Simulation",
    layoutStore: DashboardLayoutStore = rememberDashboardLayoutStore(),
    shiftLightStore: ShiftLightSettingsStore = rememberShiftLightSettingsStore(),
) {
    RideMode()
    val time = rememberDashboardClock()
    val readings = remember(state) { DashboardReadings.from(state) }
    var layout by rememberSaveable { mutableStateOf(layoutStore.load()) }
    var shiftLight by remember { mutableStateOf(shiftLightStore.load()) }
    var editingShiftLight by rememberSaveable { mutableStateOf(false) }
    val shiftPhase = rememberShiftLightPhase(readings.rpm, shiftLight)
    Box(modifier.fillMaxSize()) {
        when (layout) {
            DashboardLayout.SPORT -> SportDashboardPanel(readings, time)
            DashboardLayout.CLASSIC -> ClassicDashboardPanel(readings, time)
        }
        // Same place on every display, so the rider always finds it in one spot.
        DashboardToolbar(
            selected = layout,
            onSelect = { selected ->
                layout = selected
                layoutStore.save(selected)
            },
            sourceLabel = sourceLabel,
            onDiagnostics = onDiagnostics,
            onShiftLight = { editingShiftLight = true },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = TOOLBAR_BOTTOM_INSET),
        )
        // Over every display and the toolbar, as the warning must win over everything else.
        ShiftLightOverlay(shiftPhase, shiftLight.frequencyHz, Modifier.fillMaxSize())
    }
    if (editingShiftLight) {
        ShiftLightSettingsDialog(
            settings = shiftLight,
            onChange = { changed ->
                shiftLight = changed
                shiftLightStore.save(changed)
            },
            onDismiss = { editingShiftLight = false },
        )
    }
}

/** Distance from the bottom edge to the shared toolbar. Displays keep their bottom center free. */
internal val TOOLBAR_BOTTOM_INSET = 14.dp
