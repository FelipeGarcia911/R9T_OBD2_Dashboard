package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.felipeg.r9t_obd2_dashboard.R
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.ControlShape
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The app's own settings, apart from the display's: a gear in a TFT control box opening [items]
 * (adapter, diagnostics and, in debug builds, the simulation).
 */
@Composable
internal fun AppMenu(items: List<DashboardMenuItem>, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(R.string.app_menu)
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .background(TftColors.GroundEdge.copy(alpha = .6f), ControlShape)
                .border(1.dp, TftColors.ControlOutline, ControlShape),
        )
        Box(
            Modifier
                .clickable(role = Role.Button) { expanded = true }
                .semantics { contentDescription = description }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(18.dp)) { drawGear(TftColors.White) }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(item.label) }, onClick = {
                    expanded = false
                    item.onClick()
                })
            }
        }
    }
}

/** A plain eight-tooth gear with a round hole, filling the draw area. */
internal fun DrawScope.drawGear(color: Color) {
    val outer = size.minDimension / 2
    val root = outer * .74f
    val teeth = 8
    val step = 2 * PI / teeth
    fun point(angle: Double, radius: Float) =
        Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
    val gear = Path().apply {
        fillType = PathFillType.EvenOdd
        for (tooth in 0 until teeth) {
            val middle = tooth * step - PI / 2
            val corners = listOf(
                point(middle - step * .30, root),
                point(middle - step * .17, outer),
                point(middle + step * .17, outer),
                point(middle + step * .30, root),
            )
            corners.forEachIndexed { index, corner ->
                if (tooth == 0 && index == 0) moveTo(corner.x, corner.y) else lineTo(corner.x, corner.y)
            }
        }
        close()
        addOval(Rect(center, outer * .32f))
    }
    drawPath(gear, color)
}
