package com.felipeg.r9t_obd2_dashboard

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticSession
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.BluetoothPairedAdapters
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.ElmDiagnosticSession
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.ElmSessionLogs
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.LastAdapterStore
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.LogSettings
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.PairedAdapterSource
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.RfcommElmTransport
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.SharedPreferencesLastAdapterStore
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.SharedPreferencesLogSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope

/**
 * Builds the app's long-lived objects in one place, so screens receive what they need instead of
 * constructing Android-backed classes themselves.
 */
internal class AppContainer(context: Context) {
    private val application = context.applicationContext

    val logSettings: LogSettings = SharedPreferencesLogSettings(application)
    val sessionLogs = ElmSessionLogs(File(application.noBackupFilesDir, "elm327-logs"))
    val pairedAdapters: PairedAdapterSource = BluetoothPairedAdapters(application)
    val lastAdapter: LastAdapterStore = SharedPreferencesLastAdapterStore(application)

    /** A live ELM327 session over Bluetooth, logging in detail when [logSettings] asks for it. */
    fun liveSession(scope: CoroutineScope): DiagnosticSession = ElmDiagnosticSession(
        scope,
        RfcommElmTransport(application),
        startLog = { if (logSettings.detailedLogging) sessionLogs.start() else null },
    )
}

internal class R9tApplication : Application() {
    val container by lazy { AppContainer(this) }
}

@Composable
internal fun appContainer(): AppContainer = (LocalContext.current.applicationContext as R9tApplication).container
