package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftLabelBox
import androidx.compose.ui.res.stringResource
import com.felipeg.r9t_obd2_dashboard.R

/**
 * Controls shared by every display: the dashboard menu and one tab per display, styled as TFT
 * label boxes. DashboardScreen places it in the same spot for every display.
 */
@Composable
internal fun DashboardToolbar(
    selected: DashboardLayout,
    onSelect: (DashboardLayout) -> Unit,
    sourceLabel: String,
    onDiagnostics: () -> Unit,
    onShiftLight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DashboardMenu(sourceLabel, onDiagnostics, onShiftLight)
        DisplaySelector(selected, onSelect)
    }
}

/** One tab per display. Adding a [DashboardLayout] adds its tab; nothing here changes. */
@Composable
internal fun DisplaySelector(
    selected: DashboardLayout,
    onSelect: (DashboardLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectorDescription = stringResource(R.string.dashboard_display_selector)
    Row(
        modifier.selectableGroup().semantics { contentDescription = selectorDescription },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DashboardLayout.entries.forEach { layout ->
            TftLabelBox(
                stringResource(layout.title),
                active = layout == selected,
                modifier = Modifier.selectable(
                    selected = layout == selected,
                    role = Role.Tab,
                    onClick = { onSelect(layout) },
                ),
                textSize = 13.dp,
            )
        }
    }
}

@Composable
private fun DashboardMenu(sourceLabel: String, onDiagnostics: () -> Unit, onShiftLight: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val menuDescription = stringResource(R.string.dashboard_menu)
    Box {
        TftLabelBox(
            "···",
            active = false,
            modifier = Modifier
                .clickable(role = Role.Button) { expanded = true }
                .semantics { contentDescription = menuDescription },
            textSize = 13.dp,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(sourceLabel) }, onClick = {}, enabled = false)
            DropdownMenuItem(text = { Text(stringResource(R.string.dashboard_menu_shift_light)) }, onClick = {
                expanded = false
                onShiftLight()
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.dashboard_menu_diagnostics)) }, onClick = {
                expanded = false
                onDiagnostics()
            })
        }
    }
}
