package com.felipeg.r9t_obd2_dashboard.diagnostics.data

import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticStage
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticState
import com.felipeg.r9t_obd2_dashboard.diagnostics.application.DiagnosticTraceEntry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Pins the JSON Lines format that ReplayDiagnosticSession reads back. */
class ElmSessionLogsTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun writesOneJsonObjectPerEntryAndOmitsMissingFields() = runBlocking {
        val directory = File(folder.root, "elm327-logs")
        val log = ElmSessionLogs(directory).start()
        log.append(ElmLogEvent.EVENT, DiagnosticTraceEntry(0, DiagnosticStage.BLUETOOTH, "Opening"))
        log.append(
            ElmLogEvent.RX,
            DiagnosticTraceEntry(1_000, DiagnosticStage.ADAPTER, "Identify adapter", "ATI", "ELM327 v1.5\r>"),
        )

        val file = directory.listFiles()!!.single()
        assertTrue(file.name, file.name.matches(Regex("elm327-\\d+-[0-9a-f-]{36}\\.jsonl")))
        val lines = file.readLines()
        assertEquals(2, lines.size)

        val first = JSONObject(lines[0])
        assertEquals(
            setOf("timestamp", "elapsedMs", "event", "stage", "description"),
            first.keys().asSequence().toSet(),
        )
        assertEquals("1970-01-01T00:00:00Z", first.getString("timestamp"))
        assertEquals("EVENT", first.getString("event"))
        assertEquals("BLUETOOTH", first.getString("stage"))
        assertEquals("Opening", first.getString("description"))

        val second = JSONObject(lines[1])
        assertEquals("1970-01-01T00:00:01Z", second.getString("timestamp"))
        assertEquals("RX", second.getString("event"))
        assertEquals("ADAPTER", second.getString("stage"))
        assertEquals("ATI", second.getString("command"))
        assertEquals("ELM327 v1.5\r>", second.getString("response"))
        assertTrue(second.getLong("elapsedMs") >= first.getLong("elapsedMs"))
    }

    @Test
    fun listsOnlyNonEmptySessionLogsNewestFirst() = runBlocking {
        val directory = folder.newFolder("logs")
        File(directory, "elm327-1000-a.jsonl").writeText("{}\n")
        File(directory, "elm327-2000-b.jsonl").writeText("{}\n")
        File(directory, "elm327-3000-c.jsonl").createNewFile()
        File(directory, "notes.txt").writeText("not a log")
        File(directory, "renamed.jsonl").writeText("{}\n")

        assertEquals(
            listOf(
                SessionLogFile("renamed.jsonl", startedAtMillis = null),
                SessionLogFile("elm327-2000-b.jsonl", startedAtMillis = 2_000),
                SessionLogFile("elm327-1000-a.jsonl", startedAtMillis = 1_000),
            ),
            ElmSessionLogs(directory).list(),
        )
    }

    @Test
    fun listIsEmptyBeforeTheFirstSession() = runBlocking {
        assertEquals(emptyList<SessionLogFile>(), ElmSessionLogs(File(folder.root, "missing")).list())
    }

    @Test
    fun exportsTheWholeLog() = runBlocking {
        val directory = folder.newFolder("logs")
        val content = "{\"event\":\"EVENT\"}\n{\"event\":\"SESSION_END\"}\n"
        File(directory, "elm327-1000-a.jsonl").writeText(content)
        val destination = ByteArrayOutputStream()

        ElmSessionLogs(directory).export("elm327-1000-a.jsonl") { destination }

        assertEquals(content, destination.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun exportRejectsPathsOutsideTheLogDirectory() {
        val logs = ElmSessionLogs(folder.newFolder("logs"))
        listOf("../elm327-1.jsonl", "nested/elm327-1.jsonl", "elm327-1.txt").forEach { name ->
            assertThrows(name, IllegalArgumentException::class.java) {
                runBlocking { logs.export(name) { ByteArrayOutputStream() } }
            }
        }
    }

    @Test
    fun exportFailsWhenTheDestinationCannotBeOpened() {
        val directory = folder.newFolder("logs")
        File(directory, "elm327-1000-a.jsonl").writeText("{}\n")
        assertThrows(IOException::class.java) {
            runBlocking { ElmSessionLogs(directory).export("elm327-1000-a.jsonl") { null } }
        }
    }

    @Test
    fun startReportsALogFailureWhenTheDirectoryCannotBeCreated() {
        val notADirectory = folder.newFile("elm327-logs")
        assertThrows(ElmLogException::class.java) {
            runBlocking { ElmSessionLogs(notADirectory).start() }
        }
    }

    @Test
    fun sessionLogRecordsEachCommandBeforeItsReplyAndEndsTheSession() = runBlocking {
        val directory = folder.newFolder("logs")
        val logs = ElmSessionLogs(directory)
        val transport = ScriptedTransport { command ->
            if (command == "0100") "UNABLE TO CONNECT\r>" else initReply(command)
        }
        val session = ElmDiagnosticSession(this, transport, startLog = { logs.start() })
        try {
            session.start("paired-device")
            withTimeout(2_000) { session.state.filterIsInstance<DiagnosticState.Failed>().first() }
        } finally {
            session.stop()
        }

        val entries = directory.listFiles()!!.single().readLines().map(::JSONObject)
        assertEquals("Opening direct Bluetooth link to ELM327", entries.first().getString("description"))
        assertEquals("SESSION_END", entries.last().getString("event"))
        assertEquals("Session ended", entries.last().getString("description"))

        val sent = entries.filter { it.getString("event") == "TX_ATTEMPT" }.map { it.getString("command") }
        assertEquals(listOf("ATZ", "ATI", "ATE0", "ATH0", "ATSPA6", "0100"), sent)
        entries.forEachIndexed { index, entry ->
            if (entry.getString("event") == "RX") {
                val previous = entries[index - 1]
                assertEquals("TX_ATTEMPT", previous.getString("event"))
                assertEquals(previous.getString("command"), entry.getString("command"))
            }
        }

        // Replay maps the description back to the DiagnosticError by name.
        val error = entries.single { it.getString("event") == "ERROR" }
        assertEquals("no ecu data", error.getString("description"))
        assertEquals("ECU", error.getString("stage"))
        assertFalse(error.has("command"))

        // Replay rejects captures whose elapsed time goes backwards.
        val elapsed = entries.map { it.getLong("elapsedMs") }
        assertEquals(elapsed.sorted(), elapsed)
    }
}
