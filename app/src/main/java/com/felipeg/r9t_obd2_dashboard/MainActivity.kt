package com.felipeg.r9t_obd2_dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.felipeg.r9t_obd2_dashboard.ui.theme.R9T_OBD2_DashboardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            R9T_OBD2_DashboardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    DiagnosticContent(Modifier.padding(padding))
                }
            }
        }
    }
}
