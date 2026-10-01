package com.stresssense.app.data.local

import androidx.room.*
import com.stresssense.app.data.models.StressScan
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for stress scan operations.
 */
@Dao
interface StressScanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: StressScan): Long

    @Query("SELECT * FROM stress_scans ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestScan(): StressScan?

    @Query("SELECT * FROM stress_scans ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestScan(): Flow<StressScan?>

    @Query("SELECT * FROM stress_scans ORDER BY timestamp DESC")
    fun observeAllScans(): Flow<List<StressScan>>

    @Query("SELECT * FROM stress_scans WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    fun getScansInRange(startTime: Long, endTime: Long): Flow<List<StressScan>>

    @Query("SELECT * FROM stress_scans WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    fun getScansFrom(startTime: Long): Flow<List<StressScan>>

    @Query("SELECT * FROM stress_scans WHERE scanType = :type ORDER BY timestamp DESC")
    fun getScansByType(type: String): Flow<List<StressScan>>

    @Query("SELECT AVG(finalScore) FROM stress_scans WHERE timestamp >= :startTime")
    suspend fun getAverageScoreSince(startTime: Long): Double?

    @Delete
    suspend fun deleteScan(scan: StressScan)

    @Query("DELETE FROM stress_scans")
    suspend fun deleteAllScans()
}
