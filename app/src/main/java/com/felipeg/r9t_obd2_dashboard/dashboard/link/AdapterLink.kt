package com.felipeg.r9t_obd2_dashboard.dashboard.link

/** The adapter link as the dashboard's connection lamp shows it. */
internal enum class LinkState {
    /** The ECU is answering. */
    CONNECTED,

    /** Opening Bluetooth, waking the ELM327 or waiting for the ECU. */
    CONNECTING,

    /** A live link dropped and the session is retrying on its own. */
    RECONNECTING,

    /** The last attempt failed and needs the rider. */
    FAILED,
    DISCONNECTED,
}

internal enum class LampColor { GREEN, AMBER, RED, DIM }

/** How the lamp looks: its color, how fast it flashes (0 Hz is steady), and whether the link glyph is joined. */
internal data class LinkLamp(val color: LampColor, val blinkHz: Int, val joined: Boolean)

/**
 * TFT indicator conventions: green and steady when working, amber and flashing while something is
 * in progress (faster when a live link was lost), red when the rider must act, and dim when off.
 */
internal object LinkLampRules {
    fun lamp(state: LinkState): LinkLamp = when (state) {
        LinkState.CONNECTED -> LinkLamp(LampColor.GREEN, blinkHz = 0, joined = true)
        LinkState.CONNECTING -> LinkLamp(LampColor.AMBER, blinkHz = 1, joined = false)
        LinkState.RECONNECTING -> LinkLamp(LampColor.AMBER, blinkHz = 2, joined = false)
        LinkState.FAILED -> LinkLamp(LampColor.RED, blinkHz = 0, joined = false)
        LinkState.DISCONNECTED -> LinkLamp(LampColor.DIM, blinkHz = 0, joined = false)
    }

    /** Whether the rider can start a connection from here, rather than stop one. */
    fun canConnect(state: LinkState): Boolean = state == LinkState.DISCONNECTED || state == LinkState.FAILED
}
