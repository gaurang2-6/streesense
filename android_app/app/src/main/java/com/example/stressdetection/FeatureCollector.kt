package com.example.stressdetection

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Collects and aggregates interaction features for stress detection.
 * This class handles:
 * - Typing behavior (speed, corrections, pauses)
 * - Touch patterns (pressure, velocity, duration)
 * - Session metrics
 */
class FeatureCollector(private val context: Context) {

    // Typing metrics
    private val keyPressTimestamps = mutableListOf<Long>()
    private val keyHoldDurations = mutableListOf<Long>()
    private var backspaceCount = 0
    private var totalKeyPresses = 0
    private var lastKeyDownTime = 0L

    // Touch metrics
    private val touchPressures = mutableListOf<Float>()
    private val touchDurations = mutableListOf<Long>()
    private val swipeVelocities = mutableListOf<Float>()
    private var touchStartTime = 0L
    private var touchStartX = 0f
    private var touchStartY = 0f

    // Session metrics
    private var sessionStartTime = System.currentTimeMillis()

    companion object {
        private const val FEATURE_WINDOW_MS = 60000L // 1 minute window
    }

    // ===================== TYPING BEHAVIOR =====================

    fun onKeyDown(keyCode: Int) {
        val now = System.currentTimeMillis()
        lastKeyDownTime = now
        keyPressTimestamps.add(now)
        totalKeyPresses++

        if (keyCode == KeyEvent.KEYCODE_DEL) {
            backspaceCount++
        }

        // Clean old data
        pruneOldData()
    }

    fun onKeyUp(keyCode: Int) {
        val now = System.currentTimeMillis()
        if (lastKeyDownTime > 0) {
            keyHoldDurations.add(now - lastKeyDownTime)
        }
    }

    /**
     * Calculate typing speed in characters per second
     */
    fun getTypingSpeed(): Float {
        if (keyPressTimestamps.size < 2) return 0f
        
        val timeSpan = keyPressTimestamps.last() - keyPressTimestamps.first()
        if (timeSpan <= 0) return 0f
        
        return (keyPressTimestamps.size.toFloat() / timeSpan) * 1000f
    }

    /**
     * Ratio of backspaces to total key presses (error indicator)
     */
    fun getBackspaceRatio(): Float {
        if (totalKeyPresses == 0) return 0f
        return backspaceCount.toFloat() / totalKeyPresses
    }

    /**
     * Average time a key is held down
     */
    fun getMeanKeyHoldTime(): Float {
        if (keyHoldDurations.isEmpty()) return 0f
        return keyHoldDurations.average().toFloat()
    }

    // ===================== TOUCH PATTERNS =====================

    fun onTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartTime = System.currentTimeMillis()
                touchStartX = event.x
                touchStartY = event.y
                touchPressures.add(event.pressure)
            }
            MotionEvent.ACTION_UP -> {
                val duration = System.currentTimeMillis() - touchStartTime
                touchDurations.add(duration)

                // Calculate swipe velocity
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                
                if (duration > 0) {
                    val velocity = distance / duration * 1000 // pixels per second
                    swipeVelocities.add(velocity)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                touchPressures.add(event.pressure)
            }
        }
        pruneOldData()
    }

    /**
     * Average touch pressure (0.0 - 1.0 typically)
     */
    fun getAverageTouchPressure(): Float {
        if (touchPressures.isEmpty()) return 0.5f
        return touchPressures.average().toFloat()
    }

    /**
     * Touch pressure variance (higher = more erratic)
     */
    fun getTouchPressureVariance(): Float {
        if (touchPressures.size < 2) return 0f
        val mean = touchPressures.average()
        return touchPressures.map { (it - mean) * (it - mean) }.average().toFloat()
    }

    /**
     * Average swipe velocity
     */
    fun getAverageSwipeVelocity(): Float {
        if (swipeVelocities.isEmpty()) return 0f
        return swipeVelocities.average().toFloat()
    }

    // ===================== SESSION METRICS =====================

    /**
     * Current session length in seconds
     */
    fun getSessionLength(): Float {
        return (System.currentTimeMillis() - sessionStartTime) / 1000f
    }

    fun resetSession() {
        sessionStartTime = System.currentTimeMillis()
    }

    // External (Global) metrics from Accessibility Service
    private var globalTypingSpeed = 0f
    private var globalErrorRate = 0f
    private var globalScrollRate = 0f
    private var lastGlobalUpdate = 0L

    fun setGlobalMetrics(speed: Float, errorRate: Float, scrollRate: Float) {
        globalTypingSpeed = speed
        globalErrorRate = errorRate
        globalScrollRate = scrollRate
        lastGlobalUpdate = System.currentTimeMillis()
    }

    /**
     * Get normalized feature vector for ML model input
     * Order: [typing_speed, backspace_ratio, touch_pressure, session_length]
     */
    fun getFeatureVector(): FloatArray {
        // Use global metrics if they are recent (< 1 minute old)
        val useGlobal = (System.currentTimeMillis() - lastGlobalUpdate) < 60000 && globalTypingSpeed > 0

        val rawSpeed = if (useGlobal) globalTypingSpeed else getTypingSpeed()
        val rawError = if (useGlobal) globalErrorRate else getBackspaceRatio()
        
        // Map scroll rate to pressure slot if using global data (as intensity proxy)
        // Normalizing scroll rate (e.g., 0-5 scrolls/sec) to pressure (0-1)
        val rawPressure = if (useGlobal) (globalScrollRate / 5f).coerceIn(0f, 1f) else getAverageTouchPressure()

        // Normalize features to roughly (-2, 2) range for the model
        val typingSpeed = (rawSpeed - 5.0f) / 1.5f
        val backspaceRatio = rawError * 5f
        val touchPressure = (rawPressure - 0.6f) / 0.2f
        val sessionLength = (getSessionLength() - 60f) / 60f

        return floatArrayOf(typingSpeed, backspaceRatio, touchPressure, sessionLength)
    }

    /**
     * Get human-readable metrics for display
     */
    fun getDisplayMetrics(): Map<String, String> {
        return mapOf(
            "Typing Speed" to "%.1f chars/sec".format(getTypingSpeed()),
            "Error Rate" to "%.1f%%".format(getBackspaceRatio() * 100),
            "Touch Pressure" to "%.2f".format(getAverageTouchPressure()),
            "Session Time" to "%.0f sec".format(getSessionLength()),
            "Swipe Speed" to "%.0f px/sec".format(getAverageSwipeVelocity())
        )
    }

    // ===================== UTILITIES =====================

    private fun pruneOldData() {
        val cutoff = System.currentTimeMillis() - FEATURE_WINDOW_MS
        keyPressTimestamps.removeAll { it < cutoff }
        
        // Keep lists from growing too large
        if (touchPressures.size > 500) {
            touchPressures.subList(0, 250).clear()
        }
        if (swipeVelocities.size > 100) {
            swipeVelocities.subList(0, 50).clear()
        }
    }

    fun clear() {
        keyPressTimestamps.clear()
        keyHoldDurations.clear()
        touchPressures.clear()
        touchDurations.clear()
        swipeVelocities.clear()
        backspaceCount = 0
        totalKeyPresses = 0
        sessionStartTime = System.currentTimeMillis()
    }
}
