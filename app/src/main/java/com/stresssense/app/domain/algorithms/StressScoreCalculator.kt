package com.stresssense.app.domain.algorithms

import javax.inject.Inject

/**
 * Composite stress score calculator.
 *
 * Combines scores from multiple sensing modalities with weighted averaging:
 * - HRV (PPG):    50% weight — primary physiological stress indicator
 * - Voice:        25% weight — vocal biomarkers (pitch, jitter, shimmer)
 * - Facial:       15% weight — blink rate, expression analysis
 * - Typing:        5% weight — passive keyboard behavior
 * - Usage:         5% weight — passive phone usage patterns
 *
 * If optional modalities are unavailable, weights are re-normalized
 * among available inputs so the total always sums to 1.0.
 */
class StressScoreCalculator @Inject constructor() {

    companion object {
        const val WEIGHT_HRV = 0.50
        const val WEIGHT_VOICE = 0.25
        const val WEIGHT_FACIAL = 0.15
        const val WEIGHT_TYPING = 0.05
        const val WEIGHT_USAGE = 0.05
    }

    /**
     * Map RMSSD to a stress score (0–100).
     *
     * Typical healthy RMSSD ranges:
     * - >70 ms  → Very low stress (10–25)
     * - 50–70   → Low stress (26–40)
     * - 35–50   → Moderate stress (41–55)
     * - 20–35   → High stress (56–75)
     * - <20 ms  → Very high stress (76–95)
     *
     * Uses linear interpolation within each band for smooth transitions.
     */
    fun calculateHRVStressScore(rmssd: Double): Int {
        return when {
            rmssd >= 70 -> linearMap(rmssd, 70.0, 120.0, 25.0, 10.0)
            rmssd >= 50 -> linearMap(rmssd, 50.0, 70.0, 40.0, 26.0)
            rmssd >= 35 -> linearMap(rmssd, 35.0, 50.0, 55.0, 41.0)
            rmssd >= 20 -> linearMap(rmssd, 20.0, 35.0, 75.0, 56.0)
            else -> linearMap(rmssd, 5.0, 20.0, 95.0, 76.0)
        }.toInt().coerceIn(0, 100)
    }

    /**
     * Compute the final composite stress score from all available modalities.
     *
     * Re-normalizes weights if some modality scores are null,
     * so the result always reflects the relative importance of each input.
     */
    fun computeFinalScore(
        hrvScore: Int,
        voiceScore: Int? = null,
        facialScore: Int? = null,
        typingScore: Int? = null,
        usageScore: Int? = null
    ): Int {
        var total = hrvScore * WEIGHT_HRV
        var weights = WEIGHT_HRV

        voiceScore?.let {
            total += it * WEIGHT_VOICE
            weights += WEIGHT_VOICE
        }
        facialScore?.let {
            total += it * WEIGHT_FACIAL
            weights += WEIGHT_FACIAL
        }
        typingScore?.let {
            total += it * WEIGHT_TYPING
            weights += WEIGHT_TYPING
        }
        usageScore?.let {
            total += it * WEIGHT_USAGE
            weights += WEIGHT_USAGE
        }

        return (total / weights).toInt().coerceIn(0, 100)
    }

    /**
     * Get a human-readable label for a stress score.
     */
    fun getStressLabel(score: Int): String {
        return when {
            score <= 25 -> "Very Low"
            score <= 40 -> "Low"
            score <= 55 -> "Moderate"
            score <= 75 -> "High"
            else -> "Very High"
        }
    }

    /**
     * Get the stress level category for color coding.
     */
    fun getStressLevel(score: Int): StressLevel {
        return when {
            score <= 30 -> StressLevel.LOW
            score <= 60 -> StressLevel.MODERATE
            else -> StressLevel.HIGH
        }
    }

    /**
     * Linear interpolation between two ranges.
     */
    private fun linearMap(
        value: Double,
        inMin: Double, inMax: Double,
        outMin: Double, outMax: Double
    ): Double {
        val clamped = value.coerceIn(minOf(inMin, inMax), maxOf(inMin, inMax))
        return outMin + (outMax - outMin) * ((clamped - inMin) / (inMax - inMin))
    }

    enum class StressLevel {
        LOW,
        MODERATE,
        HIGH
    }
}
