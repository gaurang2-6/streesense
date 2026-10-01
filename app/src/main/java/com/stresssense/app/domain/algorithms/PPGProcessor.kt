package com.stresssense.app.domain.algorithms

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import kotlin.math.abs

/**
 * PPG (Photoplethysmography) Signal Processor.
 *
 * Captures the red channel intensity from camera frames when the user's
 * finger covers the rear camera with flash on. The pulsatile blood flow
 * modulates the red channel, creating a PPG waveform.
 *
 * Pipeline:
 * 1. Extract mean red channel from center 50% of each frame
 * 2. Verify finger contact (red > 200, green/blue < 50)
 * 3. Output time-series for downstream HRV processing
 */
class PPGProcessor {

    companion object {
        /** Minimum red channel mean to confirm finger contact */
        const val MIN_RED_THRESHOLD = 200

        /** Maximum green/blue channel mean when finger is covering camera */
        const val MAX_NON_RED_THRESHOLD = 50

        /** Target sampling rate (frames per second) */
        const val TARGET_FPS = 30

        /** Duration of PPG capture in seconds */
        const val CAPTURE_DURATION_SECONDS = 20

        /** Total expected frames */
        const val EXPECTED_FRAMES = TARGET_FPS * CAPTURE_DURATION_SECONDS
    }

    /** Represents a single PPG data point */
    data class PPGDataPoint(
        val timestamp: Long,       // Time in milliseconds
        val redMean: Double,       // Mean red channel value
        val greenMean: Double,     // Mean green channel (for validation)
        val blueMean: Double       // Mean blue channel (for validation)
    )

    /** Result of finger contact detection */
    data class FingerContactResult(
        val isFingerDetected: Boolean,
        val redMean: Double,
        val greenMean: Double,
        val blueMean: Double
    )

    /**
     * Extract RGB channel means from the center 50% of a bitmap frame.
     * The center crop avoids edge artifacts from the camera lens.
     */
    fun extractChannelMeans(bitmap: Bitmap): PPGDataPoint {
        val width = bitmap.width
        val height = bitmap.height

        // Define center 50% region
        val startX = width / 4
        val startY = height / 4
        val endX = width * 3 / 4
        val endY = height * 3 / 4

        var redSum = 0.0
        var greenSum = 0.0
        var blueSum = 0.0
        var pixelCount = 0

        // Sample every other pixel for performance
        for (y in startY until endY step 2) {
            for (x in startX until endX step 2) {
                val pixel = bitmap.getPixel(x, y)
                redSum += (pixel shr 16) and 0xFF
                greenSum += (pixel shr 8) and 0xFF
                blueSum += pixel and 0xFF
                pixelCount++
            }
        }

        return PPGDataPoint(
            timestamp = System.currentTimeMillis(),
            redMean = redSum / pixelCount,
            greenMean = greenSum / pixelCount,
            blueMean = blueSum / pixelCount
        )
    }

    /**
     * Check if the user's finger is properly covering the camera.
     * A covered camera with flash on produces high red and low green/blue values.
     */
    fun checkFingerContact(dataPoint: PPGDataPoint): FingerContactResult {
        return FingerContactResult(
            isFingerDetected = dataPoint.redMean > MIN_RED_THRESHOLD &&
                    dataPoint.greenMean < MAX_NON_RED_THRESHOLD &&
                    dataPoint.blueMean < MAX_NON_RED_THRESHOLD,
            redMean = dataPoint.redMean,
            greenMean = dataPoint.greenMean,
            blueMean = dataPoint.blueMean
        )
    }

    /**
     * Validate that enough frames have been captured and signal quality is adequate.
     */
    fun validateSignalQuality(dataPoints: List<PPGDataPoint>): SignalQuality {
        if (dataPoints.size < EXPECTED_FRAMES * 0.7) {
            return SignalQuality.INSUFFICIENT_DATA
        }

        // Check that most frames had valid finger contact
        val validFrames = dataPoints.count { it.redMean > MIN_RED_THRESHOLD }
        val validRatio = validFrames.toDouble() / dataPoints.size

        if (validRatio < 0.8) {
            return SignalQuality.POOR_CONTACT
        }

        // Check signal variance — a flat signal means no pulse detected
        val redValues = dataPoints.map { it.redMean }
        val mean = redValues.average()
        val variance = redValues.map { (it - mean) * (it - mean) }.average()

        if (variance < 0.1) {
            return SignalQuality.NO_PULSE_DETECTED
        }

        return SignalQuality.GOOD
    }

    enum class SignalQuality {
        GOOD,
        INSUFFICIENT_DATA,
        POOR_CONTACT,
        NO_PULSE_DETECTED
    }
}
