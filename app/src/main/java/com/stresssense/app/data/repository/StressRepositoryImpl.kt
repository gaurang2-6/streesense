package com.stresssense.app.data.repository

import com.stresssense.app.data.local.PassiveMetricDao
import com.stresssense.app.data.local.StressScanDao
import com.stresssense.app.data.local.UserBaselineDao
import com.stresssense.app.data.models.PassiveMetric
import com.stresssense.app.data.models.StressScan
import com.stresssense.app.data.models.UserBaseline
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of StressRepository backed by Room database.
 * All operations are non-blocking and data stays on-device.
 */
@Singleton
class StressRepositoryImpl @Inject constructor(
    private val stressScanDao: StressScanDao,
    private val passiveMetricDao: PassiveMetricDao,
    private val userBaselineDao: UserBaselineDao
) : StressRepository {

    // ─── Stress Scans ────────────────────────────────────────────

    override suspend fun saveScan(scan: StressScan): Long =
        stressScanDao.insertScan(scan)

    override suspend fun getLatestScan(): StressScan? =
        stressScanDao.getLatestScan()

    override fun observeLatestScan(): Flow<StressScan?> =
        stressScanDao.observeLatestScan()

    override fun observeAllScans(): Flow<List<StressScan>> =
        stressScanDao.observeAllScans()

    override fun getScansInRange(startTime: Long, endTime: Long): Flow<List<StressScan>> =
        stressScanDao.getScansInRange(startTime, endTime)

    override fun getScansFrom(startTime: Long): Flow<List<StressScan>> =
        stressScanDao.getScansFrom(startTime)

    override suspend fun getAverageScoreSince(startTime: Long): Double? =
        stressScanDao.getAverageScoreSince(startTime)

    // ─── Passive Metrics ─────────────────────────────────────────

    override suspend fun savePassiveMetric(metric: PassiveMetric): Long =
        passiveMetricDao.insertMetric(metric)

    override suspend fun getLatestPassiveMetric(): PassiveMetric? =
        passiveMetricDao.getLatestMetric()

    override fun observePassiveMetrics(): Flow<List<PassiveMetric>> =
        passiveMetricDao.observeAllMetrics()

    // ─── User Baseline ───────────────────────────────────────────

    override suspend fun saveBaseline(baseline: UserBaseline) =
        userBaselineDao.insertBaseline(baseline)

    override suspend fun getBaseline(): UserBaseline? =
        userBaselineDao.getBaseline()

    override fun observeBaseline(): Flow<UserBaseline?> =
        userBaselineDao.observeBaseline()

    override suspend fun deleteBaseline() =
        userBaselineDao.deleteBaseline()
}
