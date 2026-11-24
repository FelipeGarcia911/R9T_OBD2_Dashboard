package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticError
import java.io.IOException

/** One connection. The caller serializes exchanges and closes after an I/O failure. */
internal interface ElmTransport {
    suspend fun open(adapterId: String)
    suspend fun exchange(command: String): String
    fun takePartialResponse(): String? = null
    fun close()
}

internal class ElmTransportException(val error: DiagnosticError) : IOException()
