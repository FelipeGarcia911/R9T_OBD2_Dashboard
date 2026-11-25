package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.ElmDiagnosticSession
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.ElmSessionLogs
import com.felipeg.r9t_obd2_dashboard.diagnostics.data.ElmTransport
import com.felipeg.r9t_obd2_dashboard.diagnostics.ui.DiagnosticViewModel
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole live pipeline on a device, without the bike: the real ELM327 session, view model and
 * dashboard, over a fake adapter that answers like the reference one (55 ms per exchange,
 * single-reply requests). Compares frames and readings per second with the detailed session log
 * on and off. Results in logcat:
 *
 *     adb logcat -d -s DashboardPipeline
 */
@RunWith(AndroidJUnit4::class)
class DashboardPipelineBenchmark {
    @Test
    fun withDetailedLogging() = measure(logging = true)

    @Test
    fun withoutLogging() = measure(logging = false)

    private fun measure(logging: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val logs = File(context.cacheDir, "pipeline-bench-${System.nanoTime()}")
        val replies = AtomicInteger()
        val frames = FrameRecorder()
        var model: DiagnosticViewModel? = null
        var readingsPerSecond = 0.0
        try {
            showDashboard(
                DashboardLayout.SPORT,
                onActivity = frames::attach,
                state = {
                    val viewModel = remember {
                        DiagnosticViewModel { scope ->
                            ElmDiagnosticSession(
                                scope, BikeLikeAdapter(replies),
                                startLog = { if (logging) ElmSessionLogs(logs).start() else null },
                            )
                        }.also { model = it; it.connect("bench") }
                    }
                    viewModel.uiState.collectAsState().value.session
                },
            ) { scenario ->
                Thread.sleep(WARM_UP_MILLIS)
                val repliesBefore = replies.get()
                frames.recording = true
                Thread.sleep(MEASURE_MILLIS)
                frames.recording = false
                readingsPerSecond = (replies.get() - repliesBefore) * 1_000.0 / MEASURE_MILLIS
                scenario.onActivity { model?.disconnect() }
            }
        } finally {
            logs.deleteRecursively()
        }
        Log.i(
            TAG,
            "logging %s: %s · RPM/speed %.1f per s"
                .format(if (logging) "on " else "off", frames.summary(MEASURE_MILLIS), readingsPerSecond),
        )
    }

    /** Answers the handshake like the reference adapter and ECU, then a rev on every RPM request. */
    private class BikeLikeAdapter(private val rpmReplies: AtomicInteger) : ElmTransport {
        private val startedAt = System.nanoTime()

        override suspend fun open(adapterId: String) = delay(300)

        override suspend fun exchange(command: String): String {
            delay(EXCHANGE_MILLIS)
            return when (command) {
                "ATZ", "ATI" -> "ELM327 v2.3\r>"
                "ATE0", "ATH0", "ATS0", "ATSPA6" -> "OK\r>"
                "ATRV" -> "14.3V\r>"
                "0100" -> "41 00 BF DE A8 13 \r>"
                "0120" -> "41 20 80 05 B1 01 \r>"
                "0140" -> "41 40 FB CC 05 51 \r>"
                "0160" -> "41 60 00 00 00 01 \r>"
                "0180" -> "41 80 00 08 00 00 \r>"
                "ATDP" -> "AUTO, ISO 15765-4 (CAN 11/500)\r>"
                "010C0D1" -> rpmAndSpeed()
                "01051" -> "41 05 9B \r>"
                "010F1" -> "41 0F 49 \r>"
                "01421" -> "41 42 38 4B \r>"
                "015C1" -> "41 5C 88 \r>"
                else -> "NO DATA\r>"
            }
        }

        private fun rpmAndSpeed(): String {
            rpmReplies.incrementAndGet()
            val phase = ((System.nanoTime() - startedAt) / 1_000_000 % 4_000) / 4_000.0
            val raw = ((1_200 + 6_800 * sin(phase * PI)) * 4).toInt()
            val speed = (40 + 60 * sin(phase * PI)).toInt()
            return "41 0C %02X %02X 0D %02X \r>".format(raw shr 8, raw and 0xFF, speed)
        }

        override fun close() = Unit
    }

    private companion object {
        const val TAG = "DashboardPipeline"

        /** Median exchange time measured on the reference bike with the optimized build. */
        const val EXCHANGE_MILLIS = 55L
        const val WARM_UP_MILLIS = 3_000L
        const val MEASURE_MILLIS = 8_000L
    }
}
