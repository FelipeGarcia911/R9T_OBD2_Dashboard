package com.felipeg.r9t_obd2_dashboard.dashboard.ui.link

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.felipeg.r9t_obd2_dashboard.R
import com.felipeg.r9t_obd2_dashboard.dashboard.Blink
import com.felipeg.r9t_obd2_dashboard.dashboard.link.LampColor
import com.felipeg.r9t_obd2_dashboard.dashboard.link.LinkLamp
import com.felipeg.r9t_obd2_dashboard.dashboard.link.LinkLampRules
import com.felipeg.r9t_obd2_dashboard.dashboard.link.LinkState
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.ControlShape
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftColors

/**
 * The adapter connection lamp, beside the display selector on every display: a connector whose
 * two halves join while the ECU answers. Color and flashing follow [LinkLampRules]. Tapping it
 * shows the link state and the action that fits: connect, choose another adapter, or disconnect.
 */
@Composable
internal fun AdapterLinkLamp(link: LinkState, connection: DashboardConnection, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val lamp = LinkLampRules.lamp(link)
    val lit by rememberBlink(lamp.blinkHz)
    val stateText = link.text(connection.adapterName)
    val description = stringResource(R.string.link_description, stateText)
    Box(modifier) {
        Box(
            Modifier
                .fillMaxHeight()
                .background(TftColors.GroundEdge.copy(alpha = .6f), ControlShape)
                .border(1.dp, if (lamp.color == LampColor.DIM) TftColors.ControlOutline else lamp.color.tft(), ControlShape)
                .clickable(role = Role.Button) { expanded = true }
                .semantics { contentDescription = description }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(width = 26.dp, height = 14.dp)) {
                drawConnector(lamp, if (lit) lamp.color.tft() else lamp.color.tft().copy(alpha = .18f))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stateText) }, onClick = {}, enabled = false)
            if (LinkLampRules.canConnect(link)) {
                val name = connection.adapterName
                DropdownMenuItem(
                    text = {
                        Text(if (name == null) stringResource(R.string.link_connect) else stringResource(R.string.link_connect_to, name))
                    },
                    onClick = {
                        expanded = false
                        connection.connect()
                    },
                )
                connection.chooseAdapter?.let { choose ->
                    DropdownMenuItem(text = { Text(stringResource(R.string.link_choose_adapter)) }, onClick = {
                        expanded = false
                        choose()
                    })
                }
            } else {
                DropdownMenuItem(text = { Text(stringResource(R.string.link_disconnect)) }, onClick = {
                    expanded = false
                    connection.disconnect()
                })
            }
        }
    }
}

@Composable
private fun LinkState.text(adapterName: String?): String = when (this) {
    LinkState.CONNECTED ->
        if (adapterName == null) stringResource(R.string.link_connected) else stringResource(R.string.link_connected_to, adapterName)
    LinkState.CONNECTING -> stringResource(R.string.link_connecting)
    LinkState.RECONNECTING -> stringResource(R.string.link_reconnecting)
    LinkState.FAILED -> stringResource(R.string.link_failed)
    LinkState.DISCONNECTED -> stringResource(R.string.link_disconnected)
}

private fun LampColor.tft(): Color = when (this) {
    LampColor.GREEN -> TftColors.Green
    LampColor.AMBER -> TftColors.Amber
    LampColor.RED -> TftColors.Red
    LampColor.DIM -> TftColors.TickDim
}

/** Whether a lamp flashing at [hz] is lit now; ticks with the frame clock only while flashing. */
@Composable
private fun rememberBlink(hz: Int): State<Boolean> = produceState(true, hz) {
    value = true
    if (hz == 0) return@produceState
    val start = withFrameMillis { it }
    while (true) {
        withFrameMillis { now -> value = Blink.isOn(hz, now - start) }
    }
}

/**
 * A plug on the left and a socket on the right, each with its cable. Joined, the plug's pins sit
 * in the socket; apart, a gap opens between them.
 */
private fun DrawScope.drawConnector(lamp: LinkLamp, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = h * .13f
    val gap = if (lamp.joined) 0f else w * .09f
    val bodyHeight = h * .62f
    val bodyTop = (h - bodyHeight) / 2
    val radius = CornerRadius(h * .12f)
    // Plug: cable, body and two pins reaching right.
    val plugRight = w * .40f - gap
    drawLine(color, Offset(0f, h / 2), Offset(w * .10f - gap, h / 2), stroke, StrokeCap.Round)
    drawRoundRect(color, Offset(w * .10f - gap, bodyTop), Size(plugRight - (w * .10f - gap), bodyHeight), radius)
    for (pin in listOf(.34f, .66f)) {
        drawLine(color, Offset(plugRight, h * pin), Offset(plugRight + w * .12f, h * pin), stroke * .8f, StrokeCap.Round)
    }
    // Socket: body open toward the plug, and its cable.
    val socketLeft = w * .52f + gap
    val socketRight = (w * .88f + gap).coerceAtMost(w * .96f)
    drawRoundRect(color, Offset(socketLeft, bodyTop), Size(socketRight - socketLeft, bodyHeight), radius)
    drawLine(color, Offset(socketRight, h / 2), Offset(w, h / 2), stroke, StrokeCap.Round)
}
