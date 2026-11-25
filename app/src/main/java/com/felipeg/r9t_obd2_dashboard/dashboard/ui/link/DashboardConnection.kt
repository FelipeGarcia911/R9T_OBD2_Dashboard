package com.felipeg.r9t_obd2_dashboard.dashboard.ui.link

/**
 * What the connection lamp can do for the session behind the dashboard. [chooseAdapter] is null
 * for sources without a choice of adapter, such as the simulator; [adapterName] names the one
 * [connect] goes to, when known.
 */
internal data class DashboardConnection(
    val connect: () -> Unit,
    val disconnect: () -> Unit,
    val chooseAdapter: (() -> Unit)? = null,
    val adapterName: String? = null,
)
