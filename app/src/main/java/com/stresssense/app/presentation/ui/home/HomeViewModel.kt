package com.stresssense.app.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stresssense.app.data.models.StressScan
import com.stresssense.app.domain.usecases.GetHistoryUseCase
import com.stresssense.app.domain.usecases.GetLatestScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * ViewModel for the Home dashboard.
 * Provides latest scan data and today's trend for the mini chart.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getLatestScanUseCase: GetLatestScanUseCase,
    private val getHistoryUseCase: GetHistoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeLatestScan()
        observeTodayTrend()
    }

    private fun observeLatestScan() {
        viewModelScope.launch {
            getLatestScanUseCase.observe().collect { scan ->
                _uiState.update { it.copy(latestScan = scan) }
            }
        }
    }

    private fun observeTodayTrend() {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        viewModelScope.launch {
            getHistoryUseCase.scansFrom(startOfDay).collect { scans ->
                _uiState.update {
                    it.copy(todayScans = scans)
                }
            }
        }
    }
}

data class HomeUiState(
    val latestScan: StressScan? = null,
    val todayScans: List<StressScan> = emptyList()
)
