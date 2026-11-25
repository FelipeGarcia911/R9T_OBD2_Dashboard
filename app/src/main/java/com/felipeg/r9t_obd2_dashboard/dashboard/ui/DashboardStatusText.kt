package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.felipeg.r9t_obd2_dashboard.R

/** The short status a display shows in its top bar. */
@Composable
internal fun DashboardStatus.text(): String = when (this) {
    DashboardStatus.Disconnected -> stringResource(R.string.dashboard_status_disconnected)
    DashboardStatus.Connecting -> stringResource(R.string.dashboard_status_connecting)
    DashboardStatus.WaitingForAdapter -> stringResource(R.string.dashboard_status_waiting_for_adapter)
    DashboardStatus.WaitingForEcu -> stringResource(R.string.dashboard_status_waiting_for_ecu)
    is DashboardStatus.Reconnecting -> stringResource(R.string.dashboard_status_reconnecting, attempt)
    DashboardStatus.Failed -> stringResource(R.string.dashboard_status_failed)
}
