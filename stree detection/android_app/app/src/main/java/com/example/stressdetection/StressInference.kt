package com.example.stressdetection

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Handles TensorFlow Lite model loading and inference for stress detection.
 *
 * The model takes 11 normalized features and outputs 3 class probabilities:
 * [Low, Medium, High] stress via softmax.
 */
class StressInference(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val modelFileName = "stress_model.tflite"
    private var modelLoaded = false

    companion object {
        private const val TAG = "StressInference"
        const val NUM_FEATURES = 11
        const val NUM_CLASSES = 3
    }

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val modelBuffer = loadModelFile()
            interpreter = Interpreter(modelBuffer)
            modelLoaded = true

            // Verify model dimensions
            val inputShape = interpreter?.getInputTensor(0)?.shape()
            val outputShape = interpreter?.getOutputTensor(0)?.shape()
            Log.d(TAG, "TFLite model loaded. Input: ${inputShape?.contentToString()}, Output: ${outputShape?.contentToString()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TFLite model: ${e.message}")
            modelLoaded = false
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val assetManager = context.assets
        val fileDescriptor = assetManager.openFd(modelFileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun isModelLoaded(): Boolean = modelLoaded

    /**
     * Run inference on the normalized 11-feature vector.
     *
     * @param features Normalized float array of 11 features
     * @return Array of 3 probabilities [Low, Medium, High]
     */
    fun predictProbabilities(features: FloatArray): FloatArray {
        if (!modelLoaded || interpreter == null) {
            Log.w(TAG, "Model not loaded, using fallback calculation")
            return calculateFallbackProbabilities(features)
        }

        if (features.size != NUM_FEATURES) {
            Log.e(TAG, "Expected $NUM_FEATURES features, got ${features.size}. Using fallback.")
            return calculateFallbackProbabilities(features)
        }

        return try {
            // Prepare input buffer - shape [1, 11]
            val inputBuffer = ByteBuffer.allocateDirect(NUM_FEATURES * 4)
            inputBuffer.order(ByteOrder.nativeOrder())
            for (feature in features) {
                inputBuffer.putFloat(feature)
            }
            inputBuffer.rewind()

            // Prepare output buffer - shape [1, 3]
            val outputBuffer = ByteBuffer.allocateDirect(NUM_CLASSES * 4)
            outputBuffer.order(ByteOrder.nativeOrder())

            // Run inference
            interpreter?.run(inputBuffer, outputBuffer)

            // Get result
            outputBuffer.rewind()
            val probs = FloatArray(NUM_CLASSES)
            for (i in 0 until NUM_CLASSES) {
                probs[i] = outputBuffer.float
            }

            Log.d(TAG, String.format("Inference: Low=%.3f, Med=%.3f, High=%.3f", probs[0], probs[1], probs[2]))
            probs
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}")
            calculateFallbackProbabilities(features)
        }
    }

    /**
     * Legacy predict method - returns a single stress probability (0-1).
     * Maps 3-class output to a continuous score.
     */
    fun predict(features: FloatArray): Float {
        val probs = predictProbabilities(features)
        // Weighted combination: Low=0, Medium=0.5, High=1.0
        return probs[0] * 0.0f + probs[1] * 0.5f + probs[2] * 1.0f
    }

    /**
     * Fallback stress calculation using a heuristic when model is not available.
     *
     * Uses all 11 features with research-informed weights.
     * Features are expected to be z-score normalized.
     */
    private fun calculateFallbackProbabilities(features: FloatArray): FloatArray {
        // Feature weights based on research importance
        // Positive = increases stress, negative = decreases stress
        val weights = floatArrayOf(
            0.08f,   // typing_speed_avg (+)
            0.10f,   // typing_speed_variance (+)
            0.12f,   // backspace_rate (+)
            0.10f,   // app_switch_count_per_hour (+)
            0.08f,   // screen_unlock_count (+)
            0.06f,   // total_screen_time (+)
            0.05f,   // social_app_time_ratio (+)
            0.15f,   // late_night_usage_minutes (+)
            0.10f,   // movement_variance (+)
            -0.08f,  // step_count_daily (-)  More steps = less stress
            -0.08f   // call_sms_frequency_delta (-) More social = less stress
        )

        var stressScore = 0.4f  // baseline
        val n = minOf(features.size, weights.size)
        for (i in 0 until n) {
            stressScore += features[i] * weights[i]
        }
        stressScore = stressScore.coerceIn(0.05f, 0.95f)

        // Convert continuous score to 3-class probabilities
        return when {
            stressScore < 0.35f -> floatArrayOf(0.7f, 0.25f, 0.05f)
            stressScore < 0.65f -> floatArrayOf(0.15f, 0.7f, 0.15f)
            else -> floatArrayOf(0.05f, 0.25f, 0.7f)
        }
    }

    /**
     * Get stress level category from probability
     */
    fun getStressLevel(probability: Float): StressLevel {
        return when {
            probability <= 0.30f -> StressLevel.LOW
            probability <= 0.65f -> StressLevel.MEDIUM
            else -> StressLevel.HIGH
        }
    }

    /**
     * Get stress level from 3-class probabilities (argmax)
     */
    fun getStressLevelFromProbs(probs: FloatArray): StressLevel {
        val maxIdx = probs.indices.maxByOrNull { probs[it] } ?: 0
        return when (maxIdx) {
            0 -> StressLevel.LOW
            1 -> StressLevel.MEDIUM
            else -> StressLevel.HIGH
        }
    }

    /**
     * Get confidence (max probability) from 3-class output
     */
    fun getConfidence(probs: FloatArray): Float {
        return probs.maxOrNull() ?: 0f
    }

    /**
     * Get stress score (0-100) from probability
     */
    fun getStressScore(probability: Float): Int {
        return (probability * 100).toInt().coerceIn(0, 100)
    }

    /**
     * Full inference result with all metrics
     */
    fun analyze(features: FloatArray): StressResult {
        val probs = predictProbabilities(features)
        val level = getStressLevelFromProbs(probs)
        val confidence = getConfidence(probs)
        // Score: weighted average for continuous display
        val probability = probs[0] * 0.0f + probs[1] * 0.5f + probs[2] * 1.0f

        return StressResult(
            probability = probability,
            score = getStressScore(probability),
            level = level,
            confidence = confidence,
            classProbabilities = probs
        )
    }

    fun close() {
        interpreter?.close()
    }

    enum class StressLevel(val displayName: String, val colorRes: Int) {
        LOW("Low", R.color.stress_low),
        MEDIUM("Medium", R.color.stress_medium),
        HIGH("High", R.color.stress_high)
    }

    data class StressResult(
        val probability: Float,
        val score: Int,
        val level: StressLevel,
        val confidence: Float = 0f,
        val classProbabilities: FloatArray = floatArrayOf(0f, 0f, 0f)
    )
}
