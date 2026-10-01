package com.stresssense.app.domain.usecases

import com.stresssense.app.data.models.StressScan
import com.stresssense.app.data.repository.StressRepository
import javax.inject.Inject

/**
 * Saves a completed stress scan to the local database.
 */
class SaveScanUseCase @Inject constructor(
    private val repository: StressRepository
) {
    suspend operator fun invoke(scan: StressScan): Long {
        return repository.saveScan(scan)
    }
}
