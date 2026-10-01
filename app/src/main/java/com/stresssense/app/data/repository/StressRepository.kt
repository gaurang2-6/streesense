package com.stresssense.app.data.repository

import com.stresssense.app.data.models.PassiveMetric
import com.stresssense.app.data.models.StressScan
import com.stresssense.app.data.models.UserBaseline
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for all stress data operations.
 * Provides a clean API layer above Room DAOs.
 */
interface StressRepository {

    // Stress Scans
    suspend fun saveScan(scan: StressScan): Long
    suspend fun getLatestScan(): StressScan?
    fun observeLatestScan(): Flow<StressScan?>
    fun observeAllScans(): Flow<List<StressScan>>
    fun getScansInRange(startTime: Long, endTime: Long): Flow<List<StressScan>>
    fun getScansFrom(startTime: Long): Flow<List<StressScan>>
    suspend fun getAverageScoreSince(startTime: Long): Double?

    // Passive Metrics
    suspend fun savePassiveMetric(metric: PassiveMetric): Long
    suspend fun getLatestPassiveMetric(): PassiveMetric?
    fun observePassiveMetrics(): Flow<List<PassiveMetric>>

    // User Baseline
    suspend fun saveBaseline(baseline: UserBaseline)
    suspend fun getBaseline(): UserBaseline?
    fun observeBaseline(): Flow<UserBaseline?>
    suspend fun deleteBaseline()
}
