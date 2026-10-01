package com.stresssense.app.domain.algorithms

import android.graphics.PointF
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import javax.inject.Inject

/**
 * Facial stress analyzer using ML Kit Face Detection.
 *
 * Tracks facial features over a 10-second window to detect stress signals:
 * - Blink rate (normal: 15–20 blinks/min; stressed: higher or very low)
 * - Eye openness ratio (squinting may indicate tension)
 * - Smile probability (absence of smiling can correlate with stress)
 *
 * This class accumulates face detection results over time and computes
 * a facial stress score at the end of the capture window.
 */
class FaceAnalyzer @Inject constructor() {

    companion object {
        /** Capture duration in seconds */
        const val CAPTURE_DURATION_SEC = 10

        /** Eye openness threshold to detect a blink */
        const val BLINK_THRESHOLD = 0.3f

        /** Normal blink rate per minute */
        const val NORMAL_BLINK_RATE_PER_MIN = 17.0

        /** Minimum frames needed for reliable analysis */
        const val MIN_FRAMES = 30
    }

    // Accumulated data during the capture window
    private val leftEyeOpenValues = mutableListOf<Float>()
    private val rightEyeOpenValues = mutableListOf<Float>()
    private val smileProbValues = mutableListOf<Float>()
    private val timestamps = mutableListOf<Long>()
    private var blinkCount = 0
    private var wasBlinking = false

    /**
     * Reset all accumulated data for a new capture session.
     */
    fun reset() {
        leftEyeOpenValues.clear()
        rightEyeOpenValues.clear()
        smileProbValues.clear()
        timestamps.clear()
        blinkCount = 0
        wasBlinking = false
    }

    /**
     * Process a single face detection result from ML Kit.
     * Call this for each frame during the 10-second capture.
     *
     * @param face ML Kit Face detection result
     */
    fun processFace(face: Face) {
        val timestamp = System.currentTimeMillis()
        timestamps.add(timestamp)

        // Eye openness probabilities (0.0 = closed, 1.0 = fully open)
        val leftEyeOpen = face.leftEyeOpenProbability ?: 0.5f
        val rightEyeOpen = face.rightEyeOpenProbability ?: 0.5f
        val smileProb = face.smilingProbability ?: 0.0f

        leftEyeOpenValues.add(leftEyeOpen)
        rightEyeOpenValues.add(rightEyeOpen)
        smileProbValues.add(smileProb)

        // Blink detection (both eyes closing simultaneously)
        val isBlinking = leftEyeOpen < BLINK_THRESHOLD && rightEyeOpen < BLINK_THRESHOLD
        if (isBlinking && !wasBlinking) {
            blinkCount++
        }
        wasBlinking = isBlinking
    }

    /**
     * Compute the facial stress analysis result from accumulated data.
     * Call this after the capture window completes.
     *
     * @return FaceResult with all metrics and stress score, or null if insufficient data
     */
    fun computeResult(): FaceResult? {
        if (timestamps.size < MIN_FRAMES) return null

        val durationMs = timestamps.last() - timestamps.first()
        val durationSec = durationMs / 1000.0
        if (durationSec < 1.0) return null

        // Blink rate (blinks per minute)
        val blinkRate = blinkCount * (60.0 / durationSec)

        // Average eye openness
        val avgLeftEye = leftEyeOpenValues.average()
        val avgRightEye = rightEyeOpenValues.average()
        val avgEyeOpenness = ((avgLeftEye + avgRightEye) / 2.0).toFloat()

        // Average smile probability
        val avgSmile = smileProbValues.average().toFloat()

        // Compute facial stress score
        val stressScore = computeFacialStressScore(blinkRate, avgEyeOpenness, avgSmile)

        return FaceResult(
            blinkRate = blinkRate,
            avgEyeOpenness = avgEyeOpenness,
            avgSmileProbability = avgSmile,
            totalBlinks = blinkCount,
            captureDurationSec = durationSec,
            totalFrames = timestamps.size,
            stressScore = stressScore
        )
    }

    /**
     * Compute facial stress score (0–100) from facial features.
     *
     * Indicators of stress:
     * - Very high or very low blink rate (deviation from normal ~17/min)
     * - Reduced eye openness (squinting, tension)
     * - Low smile probability
     */
    private fun computeFacialStressScore(
        blinkRate: Double,
        avgEyeOpenness: Float,
        avgSmile: Float
    ): Int {
        // Blink rate score: deviation from normal 15-20/min range
        val blinkScore = when {
            blinkRate < 5  -> 75   // Very low — dissociative stress or intense focus
            blinkRate < 10 -> 55   // Low
            blinkRate in 10.0..25.0 -> 20  // Normal range
            blinkRate < 35 -> 50   // Elevated — anxiety
            blinkRate < 50 -> 70   // High — nervous
            else -> 85             // Very high — extreme anxiety
        }

        // Eye openness score: squinting correlates with tension
        val eyeScore = when {
            avgEyeOpenness > 0.7f -> 15   // Wide open — relaxed
            avgEyeOpenness > 0.5f -> 30   // Normal
            avgEyeOpenness > 0.3f -> 55   // Somewhat squinted
            else -> 75                     // Very squinted — tension
        }

        // Smile score: absence of smile may indicate negative affect
        val smileScore = when {
            avgSmile > 0.7f -> 10   // Clearly smiling — low stress
            avgSmile > 0.4f -> 25   // Slight smile
            avgSmile > 0.2f -> 45   // Neutral
            avgSmile > 0.1f -> 60   // Slightly negative
            else -> 75              // No smile — possibly stressed
        }

        // Weighted combination
        val combined = (blinkScore * 0.40 + eyeScore * 0.30 + smileScore * 0.30)
        return combined.toInt().coerceIn(0, 100)
    }

    /**
     * Get the current number of processed frames.
     */
    fun getFrameCount(): Int = timestamps.size

    /**
     * Get the estimated progress (0.0 to 1.0) of the capture.
     */
    fun getProgress(): Float {
        if (timestamps.size < 2) return 0f
        val elapsed = timestamps.last() - timestamps.first()
        return (elapsed / (CAPTURE_DURATION_SEC * 1000f)).coerceIn(0f, 1f)
    }

    /**
     * Container for facial analysis results.
     */
    data class FaceResult(
        val blinkRate: Double,          // blinks per minute
        val avgEyeOpenness: Float,      // 0.0–1.0
        val avgSmileProbability: Float,  // 0.0–1.0
        val totalBlinks: Int,
        val captureDurationSec: Double,
        val totalFrames: Int,
        val stressScore: Int             // 0–100
    )
}
