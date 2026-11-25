package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val ToolbarMuted = Color(0xFFB7C6CB)
private val ToolbarSelected = Color(0xFF35BDEF)
private val ToolbarOutline = Color(0x5591A3AD)
private val ToolbarSurface = Color(0x99000000)

/**
 * Controls shared by every dashboard layout: a tab per display and the dashboard menu.
 * Each layout only decides where to place it.
 */
@Composable
internal fun DashboardToolbar(
    selected: DashboardLayout,
    onSelect: (DashboardLayout) -> Unit,
    sourceLabel: String,
    onDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        DashboardMenu(sourceLabel, onDiagnostics)
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
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(ToolbarSurface)
            .border(1.dp, ToolbarOutline, shape)
            .selectableGroup()
            .semantics { contentDescription = "Display selector" },
    ) {
        DashboardLayout.entries.forEach { layout ->
            DisplayTab(layout.title, layout == selected) { onSelect(layout) }
        }
    }
}

@Composable
private fun DisplayTab(title: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) ToolbarSelected else ToolbarMuted
    Box(
        Modifier
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .then(if (selected) Modifier.selectedUnderline() else Modifier)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title.uppercase(), color = color, style = MaterialTheme.typography.labelMedium)
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
            Text("⋯", color = ToolbarMuted, style = MaterialTheme.typography.titleMedium)
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

/** A short cyan bar under the selected tab, drawn without affecting the tab's size. */
private fun Modifier.selectedUnderline() = drawBehind {
    val width = 18.dp.toPx()
    val y = size.height - 4.dp.toPx()
    drawLine(
        ToolbarSelected,
        Offset((size.width - width) / 2, y),
        Offset((size.width + width) / 2, y),
        strokeWidth = 2.dp.toPx(),
    )
}
