package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticTraceEntry
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class ElmLogEvent { EVENT, TX_ATTEMPT, RX, ERROR, SESSION_END }

internal fun interface ElmSessionLog {
    suspend fun append(event: ElmLogEvent, entry: DiagnosticTraceEntry)
}

internal class ElmLogException(cause: IOException) : IOException(cause)

/** A saved session log; [startedAtMillis] is null when the file name does not carry it. */
internal data class SessionLogFile(val name: String, val startedAtMillis: Long?)

internal class ElmSessionLogs(private val directory: File) {
    suspend fun start(): ElmSessionLog = withContext(Dispatchers.IO) {
        writing {
            if (!directory.mkdirs() && !directory.isDirectory) {
                throw IOException("Cannot create ELM327 log directory")
            }
            val file = File(directory, "$FILE_PREFIX${System.currentTimeMillis()}-${UUID.randomUUID()}.jsonl")
            if (!file.createNewFile()) throw IOException("Cannot create ELM327 session log")
            val startedAt = System.nanoTime()
            ElmSessionLog { event, entry ->
                val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
                withContext(Dispatchers.IO) {
                    writing {
                        val line = ElmLogCodec.encode(ElmLogRecord(event, elapsedMillis, entry))
                        synchronized(fileLock) { file.appendText("$line\n") }
                    }
                }
            }
        }
    }

    /** Saved sessions with at least one entry, newest first. */
    suspend fun list(): List<SessionLogFile> = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".jsonl") && it.length() > 0 }
            .sortedByDescending { it.name }
            .map { SessionLogFile(it.name, startedAtMillis(it.name)) }
    }

    suspend fun export(name: String, openDestination: () -> OutputStream?) = withContext(Dispatchers.IO) {
        require(name == File(name).name && name.endsWith(".jsonl"))
        val file = File(directory, name)
        val length = synchronized(fileLock) { file.length() }
        file.inputStream().buffered().use { input ->
            val destination = openDestination() ?: throw IOException("Cannot open export destination")
            destination.buffered().use { output ->
                var remaining = length
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (remaining > 0) {
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (count < 0) throw IOException("Session log ended during export")
                    output.write(buffer, 0, count)
                    remaining -= count
                }
            }
        }
    }

    private fun <T> writing(block: () -> T): T = try {
        block()
    } catch (error: IOException) {
        throw ElmLogException(error)
    }

    private companion object {
        const val FILE_PREFIX = "elm327-"
        val fileLock = Any()

        /** Session logs are named `elm327-<start time in epoch millis>-<uuid>.jsonl`. */
        fun startedAtMillis(name: String): Long? =
            name.removePrefix(FILE_PREFIX).substringBefore('-').toLongOrNull()
    }
}
