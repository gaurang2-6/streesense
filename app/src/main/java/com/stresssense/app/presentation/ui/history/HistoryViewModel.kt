package com.stresssense.app.presentation.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stresssense.app.data.models.StressScan
import com.stresssense.app.domain.usecases.GetHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * ViewModel for the History screen.
 * Provides scan data filtered by time range (Day/Week/Month) and scan type (Sensors/Surveys/Passive).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getHistoryUseCase: GetHistoryUseCase
) : ViewModel() {

    private val _selectedRange = MutableStateFlow(TimeRange.WEEK)
    private val _selectedScanTypeFilter = MutableStateFlow(ScanTypeFilter.ALL)
    private val _expandedScanIds = MutableStateFlow<Set<Long>>(emptySet())

    // Combine range, scan type filter, and expanded item IDs to produce the UI state
    val uiState: StateFlow<HistoryUiState> = combine(
        _selectedRange,
        _selectedScanTypeFilter,
        _expandedScanIds
    ) { range, scanTypeFilter, expandedIds ->
        Triple(range, scanTypeFilter, expandedIds)
    }.flatMapLatest { (range, scanTypeFilter, expandedIds) ->
        val startTime = getStartTime(range)
        getHistoryUseCase.scansFrom(startTime).map { scans ->
            val filteredScans = if (scanTypeFilter.scanTypeString != null) {
                scans.filter { it.scanType == scanTypeFilter.scanTypeString }
            } else {
                scans
            }
            HistoryUiState(
                selectedRange = range,
                selectedScanTypeFilter = scanTypeFilter,
                scans = filteredScans,
                expandedScanIds = expandedIds
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState()
    )

    fun setTimeRange(range: TimeRange) {
        _selectedRange.value = range
    }

    fun setScanTypeFilter(filter: ScanTypeFilter) {
        _selectedScanTypeFilter.value = filter
    }

    fun toggleScanExpansion(scanId: Long) {
        val current = _expandedScanIds.value
        _expandedScanIds.value = if (current.contains(scanId)) {
            current - scanId
        } else {
            current + scanId
        }
    }

    private fun getStartTime(range: TimeRange): Long {
        return when (range) {
            TimeRange.DAY -> {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            TimeRange.WEEK -> {
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -7)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }.timeInMillis
            }
            TimeRange.MONTH -> {
                Calendar.getInstance().apply {
                    add(Calendar.MONTH, -1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }.timeInMillis
            }
        }
    }
}

data class HistoryUiState(
    val selectedRange: TimeRange = TimeRange.WEEK,
    val selectedScanTypeFilter: ScanTypeFilter = ScanTypeFilter.ALL,
    val scans: List<StressScan> = emptyList(),
    val expandedScanIds: Set<Long> = emptySet()
)

enum class TimeRange(val label: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month")
}

enum class ScanTypeFilter(val label: String, val scanTypeString: String?) {
    ALL("All", null),
    SENSORS("Sensors", "ACTIVE"),
    SURVEYS("Surveys", "QUESTIONNAIRE"),
    PASSIVE("Passive", "PASSIVE")
}
