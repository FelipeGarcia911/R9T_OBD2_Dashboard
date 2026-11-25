package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Display
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * While the dashboard is shown, keeps the screen on, hides the system bars and asks for a 60 Hz
 * display mode (see [DashboardRefreshRate]). A swipe from the edge reveals the bars temporarily.
 * Everything is restored on exit.
 */
@Composable
internal fun RideMode() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        view.keepScreenOn = true
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        val restoreMode = window?.let { w -> view.display?.let { preferDashboardRefreshRate(w, it) } }
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
            restoreMode?.invoke()
        }
    }
}

/** Asks [window] for the dashboard's display mode; returns how to undo it, or null if nothing changed. */
private fun preferDashboardRefreshRate(window: Window, display: Display): (() -> Unit)? {
    fun Display.Mode.spec() = DisplayModeSpec(modeId, physicalWidth, physicalHeight, refreshRate)
    val mode = DashboardRefreshRate.preferredMode(display.supportedModes.map { it.spec() }, display.mode.spec())
        ?: return null
    val previous = window.attributes.preferredDisplayModeId
    window.attributes = window.attributes.apply { preferredDisplayModeId = mode.id }
    return { window.attributes = window.attributes.apply { preferredDisplayModeId = previous } }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
