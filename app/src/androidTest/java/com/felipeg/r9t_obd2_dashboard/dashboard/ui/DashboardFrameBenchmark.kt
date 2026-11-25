package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders each display for a few seconds with readings arriving at the field-measured rate and
 * records how long every frame took, using the platform's frame metrics. Results go to logcat
 * under [TAG] so runs before and after a change can be compared on the same device:
 *
 *     adb logcat -d -s DashboardBench
 *
 * It reports rather than asserts timing: absolute numbers depend on the device and build.
 */
@RunWith(AndroidJUnit4::class)
class DashboardFrameBenchmark {
    @Test
    fun sport() = measure(DashboardLayout.SPORT)

    @Test
    fun classic() = measure(DashboardLayout.CLASSIC)

    private fun measure(layout: DashboardLayout) {
        val frames = CopyOnWriteArrayList<Long>()
        val uiThread = CopyOnWriteArrayList<Long>()
        val renderThread = CopyOnWriteArrayList<Long>()
        var recording = false
        val metricsThread = HandlerThread("frame-metrics").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            if (recording && metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0L) {
                frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION)
                // Main thread: animations, layout and recording the drawing.
                uiThread += metrics.getMetric(FrameMetrics.ANIMATION_DURATION) +
                    metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) +
                    metrics.getMetric(FrameMetrics.DRAW_DURATION)
                // Render thread: syncing the drawing and issuing it to the GPU.
                renderThread += metrics.getMetric(FrameMetrics.SYNC_DURATION) +
                    metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION) +
                    metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION)
            }
        }
        runDashboard(
            layout,
            durationMillis = WARM_UP_MILLIS + MEASURE_MILLIS,
            onActivity = { it.window.addOnFrameMetricsAvailableListener(listener, Handler(metricsThread.looper)) },
            onTick = { elapsed -> recording = elapsed >= WARM_UP_MILLIS },
        )
        recording = false
        metricsThread.quitSafely()

        val millis = frames.map { it / 1_000_000.0 }.sorted()
        assertTrue("No frames recorded", millis.isNotEmpty())
        fun pct(p: Double) = millis[((millis.size - 1) * p).toInt()]
        fun median(nanos: List<Long>) = nanos.sorted()[nanos.size / 2] / 1_000_000.0
        val fps = millis.size * 1_000.0 / MEASURE_MILLIS
        val over16 = 100.0 * millis.count { it > 16.7 } / millis.size
        Log.i(
            TAG,
            "%s: %.1f fps · frame p50 %.1f ms · p90 %.1f · p99 %.1f · >16.7 ms %.0f%% · %d frames · UI thread p50 %.1f ms · render thread p50 %.1f ms"
                .format(
                    layout.name, fps, pct(.5), pct(.9), pct(.99), over16, millis.size,
                    median(uiThread), median(renderThread),
                ),
        )
    }

    private companion object {
        const val TAG = "DashboardBench"
        const val WARM_UP_MILLIS = 1_500L
        const val MEASURE_MILLIS = 6_000L
    }
}
