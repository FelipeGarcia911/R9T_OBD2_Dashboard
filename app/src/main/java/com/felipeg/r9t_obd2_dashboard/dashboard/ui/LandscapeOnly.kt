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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.felipeg.r9t_obd2_dashboard.R

/** Shows [content] in a landscape area and asks the rider, in [messageColor], to rotate the phone otherwise. */
@Composable
internal fun LandscapeOnly(
    messageColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            content()
        } else {
            Text(
                stringResource(R.string.dashboard_rotate),
                color = messageColor,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )
        }
    }
}
