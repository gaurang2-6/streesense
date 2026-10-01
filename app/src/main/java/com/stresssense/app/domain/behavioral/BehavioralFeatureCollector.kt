package com.stresssense.app.domain.behavioral

import android.view.MotionEvent
import kotlin.math.sqrt

/**
 * Collects and aggregates interaction features for behavioral stress detection.
 *
 * Computes an 11-feature vector matching the trained TFLite model:
 *
 *  0. typing_speed_avg         - chars/sec average in window
 *  1. typing_speed_variance    - variance of typing speed
 *  2. backspace_rate           - corrections per keystroke
 *  3. app_switch_count_per_hour- app switching frequency
 *  4. screen_unlock_count      - unlocks per hour
 *  5. total_screen_time        - minutes of screen-on per hour
 *  6. social_app_time_ratio    - fraction on social apps
 *  7. late_night_usage_minutes - usage between 11pm-4am
 *  8. movement_variance        - accelerometer-derived fidget score
 *  9. step_count_daily         - daily step count (÷1000)
 * 10. call_sms_frequency_delta - change vs baseline (z-score)
 *
 * Ported from StressDetect's FeatureCollector, re-namespaced and simplified
 * (no in-app key/touch capture — those signals come from the accessibility
 * service and are injected via [setGlobalMetrics]).
 */
class BehavioralFeatureCollector {

    // Typing metrics (in-app, optional — global metrics take precedence)
    private val keyPressTimestamps = mutableListOf<Long>()
    private val typingSpeedSamples = mutableListOf<Float>()
    private var backspaceCount = 0
    private var totalKeyPresses = 0

    // Touch metrics (optional, fed from activity dispatch if wired)
    private val touchPressures = mutableListOf<Float>()
    private val swipeVelocities = mutableListOf<Float>()
    private var touchStartTime = 0L
    private var touchStartX = 0f
    private var touchStartY = 0f

    // Session
    private var sessionStartTime = System.currentTimeMillis()

    // Movement / accelerometer
    private val accelMagnitudes = mutableListOf<Float>()

    // External (set by the service)
    private var dailyStepCount = 0f
    private var screenUnlockCount = 0
    private var appSwitchCountPerHour = 0f
    private var totalScreenTimeMinutes = 0f
    private var socialAppTimeRatio = 0f
    private var lateNightUsageMinutes = 0f
    private var callSmsFrequencyDelta = 0f

    // Global typing metrics (from the accessibility service broadcast)
    private var globalTypingSpeed = 0f
    private var globalErrorRate = 0f
    private var globalScrollRate = 0f
    private var lastGlobalUpdate = 0L

    companion object {
        private const val FEATURE_WINDOW_MS = 60_000L // 1-minute rolling window
        const val NUM_FEATURES = 11

        /** Normalization constants from the training pipeline (feature_stats.txt). */
        val FEATURE_MEANS = floatArrayOf(
            5.877251f, 1.364364f, 0.165610f, 12.307791f, 5.647415f,
            28.831104f, 0.415505f, 25.407377f, 0.787518f, 5.685288f, -0.700154f
        )
        val FEATURE_STDS = floatArrayOf(
            1.353449f, 0.734844f, 0.078197f, 6.332695f, 2.653187f,
            9.611197f, 0.137383f, 20.641841f, 0.448491f, 2.026003f, 0.654284f
        )
    }

    // ===================== GLOBAL TYPING (from accessibility service) =======

    /** Metrics pushed by the accessibility service broadcast. */
    fun setGlobalMetrics(speed: Float, errorRate: Float, scrollRate: Float) {
        globalTypingSpeed = speed
        globalErrorRate = errorRate
        globalScrollRate = scrollRate
        lastGlobalUpdate = System.currentTimeMillis()
    }

    // ===================== EXTERNAL DATA SETTERS ===========================

    /** Push an accelerometer magnitude reading for movement-variance calc. */
    fun addAccelReading(magnitude: Float) {
        accelMagnitudes.add(magnitude)
        if (accelMagnitudes.size > 500) {
            accelMagnitudes.subList(0, 250).clear()
        }
    }

    fun setStepCount(steps: Float) { dailyStepCount = steps }
    fun setScreenUnlockCount(count: Int) { screenUnlockCount = count }

    fun setUsageStats(
        appSwitches: Float,
        screenTime: Float,
        socialRatio: Float,
        lateNightMinutes: Float
    ) {
        appSwitchCountPerHour = appSwitches
        totalScreenTimeMinutes = screenTime
        socialAppTimeRatio = socialRatio
        lateNightUsageMinutes = lateNightMinutes
    }

    fun setCallSmsDelta(delta: Float) { callSmsFrequencyDelta = delta }

    // ===================== OPTIONAL IN-APP HOOKS ===========================

    fun onKeyDown(keyCode: Int) {
        val now = System.currentTimeMillis()
        keyPressTimestamps.add(now)
        totalKeyPresses++
        if (keyCode == android.view.KeyEvent.KEYCODE_DEL) backspaceCount++
        if (keyPressTimestamps.size >= 2) {
            val gap = keyPressTimestamps.last() - keyPressTimestamps[keyPressTimestamps.size - 2]
            if (gap > 0) typingSpeedSamples.add(1000f / gap)
        }
        pruneOldData()
    }

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
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                val distance = sqrt(dx * dx + dy * dy)
                if (duration > 0) swipeVelocities.add(distance / duration * 1000)
            }
            MotionEvent.ACTION_MOVE -> touchPressures.add(event.pressure)
        }
        pruneOldData()
    }

    fun getAverageTouchPressure(): Float =
        if (touchPressures.isEmpty()) 0.5f else touchPressures.average().toFloat()

    // ===================== DERIVED METRICS =================================

    private fun getTypingSpeed(): Float {
        if (keyPressTimestamps.size < 2) return 0f
        val timeSpan = keyPressTimestamps.last() - keyPressTimestamps.first()
        if (timeSpan <= 0) return 0f
        return (keyPressTimestamps.size.toFloat() / timeSpan) * 1000f
    }

    private fun getTypingSpeedVariance(): Float {
        if (typingSpeedSamples.size < 2) return 0f
        val mean = typingSpeedSamples.average()
        return typingSpeedSamples.map { (it - mean) * (it - mean) }.average().toFloat()
    }

    private fun getBackspaceRatio(): Float =
        if (totalKeyPresses == 0) 0f else backspaceCount.toFloat() / totalKeyPresses

    private fun getMovementVariance(): Float {
        if (accelMagnitudes.size < 2) return 0.1f
        val mean = accelMagnitudes.average()
        return accelMagnitudes.map { (it - mean) * (it - mean) }.average().toFloat()
    }

    /**
     * The 11-feature vector, z-score normalized with training stats.
     * Prefers recent global typing metrics (from the accessibility service)
     * over in-app capture when available.
     */
    fun getFeatureVector(): FloatArray {
        val useGlobal =
            (System.currentTimeMillis() - lastGlobalUpdate) < 60_000 && globalTypingSpeed > 0

        val raw = floatArrayOf(
            if (useGlobal) globalTypingSpeed else getTypingSpeed(),
            getTypingSpeedVariance(),
            if (useGlobal) globalErrorRate else getBackspaceRatio(),
            appSwitchCountPerHour,
            screenUnlockCount.toFloat(),
            totalScreenTimeMinutes,
            socialAppTimeRatio,
            lateNightUsageMinutes,
            getMovementVariance(),
            dailyStepCount / 1000f,
            callSmsFrequencyDelta
        )

        val normalized = FloatArray(NUM_FEATURES)
        for (i in 0 until NUM_FEATURES) {
            val std = FEATURE_STDS[i]
            normalized[i] = if (std > 0.0001f) (raw[i] - FEATURE_MEANS[i]) / std else 0f
        }
        return normalized
    }

    /** Whether global typing data is fresh enough to use. */
    fun hasGlobalTypingData(): Boolean =
        (System.currentTimeMillis() - lastGlobalUpdate) < 60_000 && globalTypingSpeed > 0

    // ===================== UTILITIES =======================================

    private fun pruneOldData() {
        val cutoff = System.currentTimeMillis() - FEATURE_WINDOW_MS
        keyPressTimestamps.removeAll { it < cutoff }
        if (touchPressures.size > 500) touchPressures.subList(0, 250).clear()
        if (swipeVelocities.size > 100) swipeVelocities.subList(0, 50).clear()
        if (typingSpeedSamples.size > 200) typingSpeedSamples.subList(0, 100).clear()
    }

    fun clear() {
        keyPressTimestamps.clear()
        typingSpeedSamples.clear()
        touchPressures.clear()
        swipeVelocities.clear()
        accelMagnitudes.clear()
        backspaceCount = 0
        totalKeyPresses = 0
        screenUnlockCount = 0
        dailyStepCount = 0f
        sessionStartTime = System.currentTimeMillis()
    }
}
