package com.stresssense.app.domain.usecases

import com.stresssense.app.data.models.StressScan
import com.stresssense.app.data.repository.StressRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Retrieves scan history with time-range filtering.
 */
class GetHistoryUseCase @Inject constructor(
    private val repository: StressRepository
) {
    fun allScans(): Flow<List<StressScan>> = repository.observeAllScans()

    fun scansInRange(startTime: Long, endTime: Long): Flow<List<StressScan>> =
        repository.getScansInRange(startTime, endTime)

    fun scansFrom(startTime: Long): Flow<List<StressScan>> =
        repository.getScansFrom(startTime)

    suspend fun averageScoreSince(startTime: Long): Double? =
        repository.getAverageScoreSince(startTime)
}
