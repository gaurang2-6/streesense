package com.stresssense.app.services.workers

import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stresssense.app.data.models.StressScan
import com.stresssense.app.data.repository.StressRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar

/**
 * Worker that runs periodically (every 6 hours) in the background to aggregate
 * passive stress signals (typing rhythm, motion jitter, phone usage stats)
 * and records a daily PASSIVE StressScan entry in Room.
 */
@HiltWorker
class DailyAggregationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: StressRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 6 * 3600 * 1000 // Last 6 hours

        // 1. Fetch usage stats and calculate usageScore
        val usageScore = getUsageScore(startTime, endTime)

        // 2. Fetch recent passive metrics (typing, motion)
        val recentMetrics = repository.observePassiveMetrics().firstOrNull()?.filter {
            it.timestamp >= startTime
        } ?: emptyList()

        val averageTypingScore = recentMetrics.mapNotNull { it.typingScore }.average().let {
            if (it.isNaN()) null else it.toInt()
        }

        val averageMotionScore = recentMetrics.mapNotNull { it.motionScore }.average().let {
            if (it.isNaN()) null else it.toInt()
        }

        // 3. Compute final passive score
        val scores = listOfNotNull(averageTypingScore, averageMotionScore, usageScore)
        val finalPassiveScore = if (scores.isNotEmpty()) {
            scores.average().toInt()
        } else {
            // Default baseline if no background data
            40
        }

        // 4. Save passive scan result
        val passiveScan = StressScan(
            timestamp = endTime,
            finalScore = finalPassiveScore,
            hrvScore = null,
            voiceScore = null,
            facialScore = null,
            heartRate = null,
            rmssd = null,
            sdnn = null,
            scanType = "PASSIVE"
        )

        repository.saveScan(passiveScan)

        return Result.success()
    }

    private fun getUsageScore(startTime: Long, endTime: Long): Int? {
        return try {
            val usageStatsManager = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            if (usageStats.isNullOrEmpty()) return null

            // Count app switches and total usage
            var activeAppCount = 0
            var totalTimeMs = 0L

            for (stat in usageStats) {
                if (stat.totalTimeInForeground > 0) {
                    activeAppCount++
                    totalTimeMs += stat.totalTimeInForeground
                }
            }

            // Calculate stress score based on usage patterns
            // Very high app usage or frequent switching can imply stress/distraction.
            // E.g., screen active > 3 hours in a 6 hour window, or > 15 apps accessed.
            var score = 30
            val hoursInForeground = totalTimeMs / 3600000.0

            if (hoursInForeground > 3.0) {
                score += 35
            } else if (hoursInForeground > 1.5) {
                score += 15
            }

            if (activeAppCount > 15) {
                score += 20
            } else if (activeAppCount > 8) {
                score += 10
            }

            // Check if there is late night usage (between 11 PM and 3 AM)
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = endTime
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            if (hour >= 23 || hour <= 3) {
                score += 10
            }

            score.coerceIn(15, 95)
        } catch (e: SecurityException) {
            // Permission not granted
            null
        } catch (e: Exception) {
            null
        }
    }
}
