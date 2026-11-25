package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Renders what this draws once into its own GPU layer and reuses it every frame until it changes,
 * so a detailed but unchanging scale costs nothing while the needle moves over it.
 */
internal fun Modifier.staticLayer(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }

/** Gives what redraws every frame its own layer, so redrawing it does not touch anything else. */
internal fun Modifier.movingLayer(): Modifier = graphicsLayer()
