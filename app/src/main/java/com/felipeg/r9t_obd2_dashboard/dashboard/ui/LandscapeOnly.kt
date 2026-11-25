package com.felipeg.r9t_obd2_dashboard.dashboard.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.felipeg.r9t_obd2_dashboard.dashboard.ui.tft.TftColors

/** Shows [content] in a landscape area and asks the rider to rotate the phone otherwise. */
@Composable
internal fun LandscapeOnly(modifier: Modifier = Modifier, content: @Composable BoxWithConstraintsScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            content()
        } else {
            Text(
                "Rotate your phone to view the dashboard.",
                color = TftColors.White,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )
        }
    }
}
