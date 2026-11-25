package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Records every rendered frame of an activity's window through the platform's frame metrics:
 * total time, main-thread work (animation, layout, recording the drawing) and render-thread
 * work (sync, GPU command issue, swap). Only frames while [recording] count.
 */
internal class FrameRecorder {
    private val total = CopyOnWriteArrayList<Long>()
    private val uiThread = CopyOnWriteArrayList<Long>()
    private val renderThread = CopyOnWriteArrayList<Long>()
    private val thread = HandlerThread("frame-metrics").apply { start() }

    @Volatile
    var recording = false

    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        if (recording && metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0L) {
            total += metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            uiThread += metrics.getMetric(FrameMetrics.ANIMATION_DURATION) +
                metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) +
                metrics.getMetric(FrameMetrics.DRAW_DURATION)
            renderThread += metrics.getMetric(FrameMetrics.SYNC_DURATION) +
                metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION) +
                metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION)
        }
    }

    fun attach(activity: Activity) = activity.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))

    val frames: Int get() = total.size

    /** One line for logcat: fps over [measuredMillis] and per-frame percentiles. */
    fun summary(measuredMillis: Long): String {
        recording = false
        thread.quitSafely()
        val millis = total.map { it / 1_000_000.0 }.sorted()
        // The screen was off or still rotating: say so instead of failing the run.
        if (millis.isEmpty()) return "no frames recorded"
        fun pct(p: Double) = millis[((millis.size - 1) * p).toInt()]
        fun median(nanos: List<Long>) = nanos.sorted()[nanos.size / 2] / 1_000_000.0
        return "%.1f fps · frame p50 %.1f ms · p90 %.1f · >16.7 ms %.0f%% · %d frames · UI thread p50 %.1f ms · render thread p50 %.1f ms"
            .format(
                millis.size * 1_000.0 / measuredMillis, pct(.5), pct(.9),
                100.0 * millis.count { it > 16.7 } / millis.size, millis.size,
                median(uiThread), median(renderThread),
            )
    }
}
