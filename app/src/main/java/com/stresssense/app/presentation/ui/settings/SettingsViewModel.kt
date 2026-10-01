package com.stresssense.app.presentation.ui.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.stresssense.app.data.repository.StressRepository
import com.stresssense.app.services.PassiveMonitoringService
import com.stresssense.app.services.workers.DailyAggregationWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 * Manages passive monitoring toggle, notification preferences, and baseline.
 * Persists preferences in SharedPreferences and controls background service / WorkManager.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: StressRepository
) : ViewModel() {

    private val prefs: SharedPreferences = context.getSharedPreferences("stress_sense_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            passiveMonitoringEnabled = prefs.getBoolean("passive_monitoring", false),
            notificationsEnabled = prefs.getBoolean("notifications", true)
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        checkBaseline()
    }

    fun togglePassiveMonitoring(enabled: Boolean) {
        prefs.edit().putBoolean("passive_monitoring", enabled).apply()
        _uiState.update { it.copy(passiveMonitoringEnabled = enabled) }

        try {
            if (enabled) {
                startPassiveMonitoring()
            } else {
                stopPassiveMonitoring()
            }
        } catch (_: Exception) {
            // If anything goes wrong starting/stopping the service or
            // scheduling work, revert the toggle so the UI stays honest.
            prefs.edit().putBoolean("passive_monitoring", !enabled).apply()
            _uiState.update { it.copy(passiveMonitoringEnabled = !enabled) }
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        prefs.edit().putBoolean("notifications", enabled).apply()
        _uiState.update { it.copy(notificationsEnabled = enabled) }
    }

    fun recalibrateBaseline() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRecalibrating = true) }
            repository.deleteBaseline()
            _uiState.update { it.copy(
                isRecalibrating = false,
                hasBaseline = false
            ) }
        }
    }

    private fun checkBaseline() {
        viewModelScope.launch {
            val baseline = repository.getBaseline()
            _uiState.update { it.copy(hasBaseline = baseline != null) }
        }
    }

    private fun startPassiveMonitoring() {
        // 1. Start foreground service for motion monitoring.
        // On Android 14+ this requires FOREGROUND_SERVICE_SPECIAL_USE or
        // FOREGROUND_SERVICE_HEALTH permission plus a <property> in the
        // manifest. If the user hasn't granted it, or the OS blocks it,
        // we catch the exception silently rather than crashing the app.
        try {
            val intent = Intent(context, PassiveMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {
            // Foreground service start failed (missing permission, OS
            // restriction, etc.). The passive monitoring simply won't run
            // until the user grants the required permission.
        }

        // 2. Schedule WorkManager task for periodic aggregation
        try {
            val workRequest = PeriodicWorkRequestBuilder<DailyAggregationWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily_passive_aggregation",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        } catch (_: Exception) {
            // WorkManager may not be initialized yet, or Hilt worker
            // factory issue. Passive monitoring service still runs.
        }
    }

    private fun stopPassiveMonitoring() {
        // 1. Stop foreground service
        val intent = Intent(context, PassiveMonitoringService::class.java)
        context.stopService(intent)

        // 2. Cancel WorkManager task
        WorkManager.getInstance(context).cancelUniqueWork("daily_passive_aggregation")
    }
}

data class SettingsUiState(
    val passiveMonitoringEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val hasBaseline: Boolean = false,
    val isRecalibrating: Boolean = false
)
