package com.stresssense.app.domain.algorithms

import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Heart Rate Variability (HRV) calculator.
 *
 * Takes R-R intervals (time between heartbeats in ms) from the signal
 * processor and computes clinically-relevant HRV metrics:
 *
 * - HR:    Heart rate in BPM
 * - SDNN:  Standard deviation of R-R intervals (overall HRV)
 * - RMSSD: Root mean square of successive R-R differences (short-term HRV)
 * - pNN50: Percentage of successive R-R differences > 50ms
 *
 * Key insight: LOW RMSSD = HIGH STRESS (reduced parasympathetic activity)
 */
class HRVCalculator @Inject constructor() {

    companion object {
        /** Maximum acceptable R-R deviation from median (±20%) */
        const val OUTLIER_THRESHOLD = 0.20

        /** Minimum R-R intervals needed for reliable HRV computation */
        const val MIN_RR_COUNT = 5

        /** Threshold for pNN50: successive difference > 50ms */
        const val PNN50_THRESHOLD_MS = 50.0

        /** Physiological limits for R-R intervals (ms) */
        const val MIN_RR_MS = 250.0   // 240 BPM
        const val MAX_RR_MS = 2000.0  // 30 BPM
    }

    /**
     * Complete HRV analysis from R-R intervals.
     *
     * @param rrIntervals List of R-R intervals in milliseconds
     * @return HRVResult with all computed metrics, or null if data is insufficient
     */
    fun calculate(rrIntervals: List<Double>): HRVResult? {
        if (rrIntervals.size < MIN_RR_COUNT) return null

        // Step 1: Remove physiologically impossible values
        val physiologicalRR = rrIntervals.filter { it in MIN_RR_MS..MAX_RR_MS }
        if (physiologicalRR.size < MIN_RR_COUNT) return null

        // Step 2: Outlier rejection — remove intervals beyond ±20% of median
        val cleanRR = removeOutliers(physiologicalRR)
        if (cleanRR.size < MIN_RR_COUNT) return null

        // Step 3: Compute metrics
        val hr = calculateHeartRate(cleanRR)
        val sdnn = calculateSDNN(cleanRR)
        val rmssd = calculateRMSSD(cleanRR)
        val pnn50 = calculatePNN50(cleanRR)

        return HRVResult(
            heartRate = hr,
            sdnn = sdnn,
            rmssd = rmssd,
            pnn50 = pnn50,
            rrIntervalCount = cleanRR.size,
            meanRR = cleanRR.average()
        )
    }

    /**
     * Remove R-R intervals that deviate more than ±20% from the median.
     * This eliminates ectopic beats and measurement artifacts.
     */
    fun removeOutliers(rrIntervals: List<Double>): List<Double> {
        val sorted = rrIntervals.sorted()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        } else {
            sorted[sorted.size / 2]
        }

        val lowerBound = median * (1.0 - OUTLIER_THRESHOLD)
        val upperBound = median * (1.0 + OUTLIER_THRESHOLD)

        return rrIntervals.filter { it in lowerBound..upperBound }
    }

    /**
     * Heart rate = 60000 / mean(R-R intervals).
     * Result is in beats per minute (BPM).
     */
    fun calculateHeartRate(rrIntervals: List<Double>): Double {
        val meanRR = rrIntervals.average()
        return if (meanRR > 0) 60000.0 / meanRR else 0.0
    }

    /**
     * SDNN — Standard Deviation of all R-R intervals.
     * Reflects overall HRV including both sympathetic and parasympathetic input.
     */
    fun calculateSDNN(rrIntervals: List<Double>): Double {
        val mean = rrIntervals.average()
        val variance = rrIntervals.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }

    /**
     * RMSSD — Root Mean Square of Successive Differences.
     * Primary short-term HRV metric reflecting parasympathetic activity.
     *
     * LOW RMSSD (<20ms) = HIGH STRESS
     * HIGH RMSSD (>70ms) = LOW STRESS
     */
    fun calculateRMSSD(rrIntervals: List<Double>): Double {
        if (rrIntervals.size < 2) return 0.0

        val successiveDiffs = (1 until rrIntervals.size).map { i ->
            val diff = rrIntervals[i] - rrIntervals[i - 1]
            diff * diff
        }

        return sqrt(successiveDiffs.average())
    }

    /**
     * pNN50 — Percentage of successive R-R differences exceeding 50ms.
     * Higher pNN50 indicates greater parasympathetic tone (lower stress).
     */
    fun calculatePNN50(rrIntervals: List<Double>): Double {
        if (rrIntervals.size < 2) return 0.0

        val totalDiffs = rrIntervals.size - 1
        val nn50Count = (1 until rrIntervals.size).count { i ->
            abs(rrIntervals[i] - rrIntervals[i - 1]) > PNN50_THRESHOLD_MS
        }

        return (nn50Count.toDouble() / totalDiffs) * 100.0
    }

    /**
     * Container for all computed HRV metrics.
     */
    data class HRVResult(
        val heartRate: Double,    // BPM
        val sdnn: Double,         // ms — overall HRV
        val rmssd: Double,        // ms — short-term HRV (key stress indicator)
        val pnn50: Double,        // % — parasympathetic tone
        val rrIntervalCount: Int, // number of valid R-R intervals used
        val meanRR: Double        // ms — average R-R interval
    )
}
