package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders each display for a few seconds with readings arriving at the field-measured rate and
 * records how long every frame took (see [FrameRecorder]). Results go to logcat so runs before
 * and after a change can be compared on the same device:
 *
 *     adb logcat -d -s DashboardBench
 *
 * Run it against the optimized build for phone-realistic numbers: `-PtestBuildType=profile`.
 * It reports rather than asserts timing: absolute numbers depend on the device and build.
 */
@RunWith(AndroidJUnit4::class)
class DashboardFrameBenchmark {
    @Test
    fun sport() = measure(DashboardLayout.SPORT)

    private fun measure(layout: DashboardLayout) {
        val frames = FrameRecorder()
        runDashboard(
            layout,
            durationMillis = WARM_UP_MILLIS + MEASURE_MILLIS,
            onActivity = frames::attach,
            onTick = { elapsed -> frames.recording = elapsed >= WARM_UP_MILLIS },
        )
        Log.i(TAG, "${layout.name}: ${frames.summary(MEASURE_MILLIS)}")
    }

    private companion object {
        const val TAG = "DashboardBench"
        const val WARM_UP_MILLIS = 1_500L
        const val MEASURE_MILLIS = 6_000L
    }
}
