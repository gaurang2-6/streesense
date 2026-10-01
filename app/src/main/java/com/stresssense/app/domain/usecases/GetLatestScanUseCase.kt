package com.stresssense.app.domain.usecases

import com.stresssense.app.data.models.StressScan
import com.stresssense.app.data.repository.StressRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Retrieves the most recent stress scan for display on the home dashboard.
 */
class GetLatestScanUseCase @Inject constructor(
    private val repository: StressRepository
) {
    suspend operator fun invoke(): StressScan? = repository.getLatestScan()

    fun observe(): Flow<StressScan?> = repository.observeLatestScan()
}
