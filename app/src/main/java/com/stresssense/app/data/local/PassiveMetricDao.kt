package com.stresssense.app.data.local

import androidx.room.*
import com.stresssense.app.data.models.PassiveMetric
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for passive monitoring metrics.
 */
@Dao
interface PassiveMetricDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetric(metric: PassiveMetric): Long

    @Query("SELECT * FROM passive_metrics ORDER BY timestamp DESC")
    fun observeAllMetrics(): Flow<List<PassiveMetric>>

    @Query("SELECT * FROM passive_metrics WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    fun getMetricsFrom(startTime: Long): Flow<List<PassiveMetric>>

    @Query("SELECT * FROM passive_metrics ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMetric(): PassiveMetric?

    @Query("DELETE FROM passive_metrics")
    suspend fun deleteAllMetrics()
}
