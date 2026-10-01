package com.stresssense.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.stresssense.app.presentation.navigation.StressSenseNavHost
import com.stresssense.app.presentation.theme.StressSenseTheme
import com.stresssense.app.services.PassiveMonitoringService
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity host for the entire StressSense app.
 * Uses Jetpack Compose for all UI rendering.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Automatically start passive monitoring service on app launch if enabled by user
        val prefs = getSharedPreferences("stress_sense_prefs", Context.MODE_PRIVATE)
        val passiveEnabled = prefs.getBoolean("passive_monitoring", false)
        if (passiveEnabled) {
            try {
                val intent = Intent(this, PassiveMonitoringService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } catch (_: Exception) {
                // Starting the foreground service can fail if POST_NOTIFICATIONS was
                // denied or the service type isn't allowed yet. Never crash on launch.
            }
        }

        enableEdgeToEdge()
        setContent {
            StressSenseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = StressSenseTheme.colors.background
                ) {
                    StressSenseNavHost()
                }
            }
        }
    }
}
