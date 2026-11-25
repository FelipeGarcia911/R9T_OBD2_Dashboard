package com.felipeg.r9t_obd2_dashboard.diagnostics.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.ElmSessionLogs
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.LogSettings
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.SessionLogFile
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

@Composable
internal fun ElmLogControls(settings: LogSettings, logs: ElmSessionLogs) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var enabled by remember(settings) { mutableStateOf(settings.detailedLogging) }
    var showSettings by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf<List<SessionLogFile>?>(null) }
    var exportName by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val name = exportName
        exportName = null
        if (uri != null && name != null) {
            scope.launch {
                notice = try {
                    logs.export(name) { resolver.openOutputStream(uri) }
                    "Session log exported."
                } catch (_: IOException) {
                    "Could not export the session log."
                } catch (_: SecurityException) {
                    "Could not access the export destination."
                }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { showSettings = true }) { Text("Settings") }
        OutlinedButton(onClick = { scope.launch { files = logs.list() } }) { Text("Session logs") }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Detailed ELM327 logging", modifier = Modifier.weight(1f))
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                settings.detailedLogging = it
                                enabled = it
                            },
                        )
                    }
                    Text("Applies to the next connection. Existing logs remain available for export.")
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } },
        )
    }

    files?.let { saved ->
        AlertDialog(
            onDismissRequest = { files = null },
            title = { Text("Export session log") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (saved.isEmpty()) Text("No saved sessions.")
                    saved.forEach { log ->
                        TextButton(onClick = {
                            exportName = log.name
                            files = null
                            export.launch(log.name)
                        }) { Text(log.label()) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { files = null }) { Text("Close") } },
        )
    }

    notice?.let { message ->
        AlertDialog(
            onDismissRequest = { notice = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("Done") } },
        )
    }
}

/** The session's start time in the phone's locale, or the file name when it has none. */
private fun SessionLogFile.label(): String {
    val time = startedAtMillis ?: return name
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))
}
