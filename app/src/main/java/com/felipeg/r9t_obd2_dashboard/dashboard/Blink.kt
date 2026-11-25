package com.felipeg.r9t_obd2_dashboard.dashboard

/** How the dashboard's lamps flash: [hz] full on/off cycles a second, starting lit. 0 Hz is steady. */
internal object Blink {
    fun isOn(hz: Int, elapsedMillis: Long): Boolean = hz == 0 || (elapsedMillis * hz * 2 / 1_000) % 2 == 0L
}
