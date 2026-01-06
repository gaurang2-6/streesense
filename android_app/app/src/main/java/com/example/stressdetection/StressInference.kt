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
 */
class StressInference(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val modelFileName = "stress_model.tflite"
    private var modelLoaded = false

    companion object {
        private const val TAG = "StressInference"
        const val NUM_FEATURES = 4
        
        // Stress level thresholds
        const val LOW_STRESS_MAX = 0.30f
        const val MEDIUM_STRESS_MAX = 0.65f
    }

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val modelBuffer = loadModelFile()
            interpreter = Interpreter(modelBuffer)
            modelLoaded = true
            Log.d(TAG, "TFLite model loaded successfully")
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
     * Run inference on the feature vector.
     * @param features Array of 4 floats: [typing_speed, backspace_ratio, touch_pressure, session_length]
     * @return Stress probability (0.0 - 1.0)
     */
    fun predict(features: FloatArray): Float {
        if (!modelLoaded || interpreter == null) {
            Log.w(TAG, "Model not loaded, using fallback calculation")
            return calculateFallbackStress(features)
        }

        if (features.size != NUM_FEATURES) {
            Log.e(TAG, "Expected $NUM_FEATURES features, got ${features.size}")
            return 0.5f
        }

        return try {
            // Prepare input buffer - shape [1, 4]
            val inputBuffer = ByteBuffer.allocateDirect(NUM_FEATURES * 4)
            inputBuffer.order(ByteOrder.nativeOrder())
            for (feature in features) {
                inputBuffer.putFloat(feature)
            }
            inputBuffer.rewind()

            // Prepare output buffer - shape [1, 1]
            val outputBuffer = ByteBuffer.allocateDirect(4)
            outputBuffer.order(ByteOrder.nativeOrder())

            // Run inference
            interpreter?.run(inputBuffer, outputBuffer)

            // Get result
            outputBuffer.rewind()
            val result = outputBuffer.float
            Log.d(TAG, "Inference result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}")
            calculateFallbackStress(features)
        }
    }

    /**
     * Fallback stress calculation when model is not available
     */
    private fun calculateFallbackStress(features: FloatArray): Float {
        // Simple heuristic based on features
        val typingSpeed = features.getOrElse(0) { 0f }
        val backspaceRatio = features.getOrElse(1) { 0f }
        val touchPressure = features.getOrElse(2) { 0f }
        val sessionLength = features.getOrElse(3) { 0f }
        
        // Higher typing speed, more backspaces, higher pressure = more stress
        val stressScore = (
            (typingSpeed * 0.15f) + 
            (backspaceRatio * 0.4f) + 
            (touchPressure * 0.25f) - 
            (sessionLength * 0.05f) + 
            0.3f  // baseline
        ).coerceIn(0f, 1f)
        
        return stressScore
    }

    /**
     * Get stress level category from probability
     */
    fun getStressLevel(probability: Float): StressLevel {
        return when {
            probability <= LOW_STRESS_MAX -> StressLevel.LOW
            probability <= MEDIUM_STRESS_MAX -> StressLevel.MEDIUM
            else -> StressLevel.HIGH
        }
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
        val probability = predict(features)
        return StressResult(
            probability = probability,
            score = getStressScore(probability),
            level = getStressLevel(probability)
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
        val level: StressLevel
    )
}
