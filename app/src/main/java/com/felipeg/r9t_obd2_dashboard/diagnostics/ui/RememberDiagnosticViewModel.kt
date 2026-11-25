package com.felipeg.r9t_obd2_dashboard.diagnostics.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticSession
import kotlinx.coroutines.CoroutineScope

/**
 * The activity's diagnostics view model for [key], so a session survives rotation and switching
 * between diagnostics and the dashboard. [createSession] runs only when the model is first made.
 */
@Composable
internal fun rememberDiagnosticViewModel(
    activity: ComponentActivity,
    key: String,
    createSession: (CoroutineScope) -> DiagnosticSession,
): DiagnosticViewModel = remember(activity, key) {
    ViewModelProvider(activity, DiagnosticViewModel.factory(createSession)).get(key, DiagnosticViewModel::class.java)
}
