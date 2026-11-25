package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** The drawing layers of a display, so tests can tell which ones redraw while readings change. */
internal enum class RenderLayer {
    /** Scale, ticks and numerals: drawn once and kept in a GPU layer. */
    SCALE,

    /** What moves with the reading: the needle, or the lit part of a bar scale. */
    READING,
}

/** Told each time a layer draws. The app's probe does nothing; tests count. */
internal fun interface RenderProbe {
    fun drawn(layer: RenderLayer)
}

internal val LocalRenderProbe = staticCompositionLocalOf { RenderProbe { } }
