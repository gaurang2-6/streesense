package com.example.stressdetection

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Collects and aggregates interaction features for stress detection.
 * Computes an 11-feature vector matching the trained ML model:
 *
 *  0. typing_speed_avg        - chars/sec average in window
 *  1. typing_speed_variance   - variance of typing speed
 *  2. backspace_rate           - corrections per keystroke
 *  3. app_switch_count_per_hour - app switching frequency
 *  4. screen_unlock_count      - unlocks per hour
 *  5. total_screen_time        - minutes of screen-on per hour
 *  6. social_app_time_ratio    - fraction on social apps
 *  7. late_night_usage_minutes - usage between 11pm-4am
 *  8. movement_variance        - accelerometer-derived fidget score
 *  9. step_count_daily         - daily step count (÷1000)
 * 10. call_sms_frequency_delta - change vs baseline (z-score)
 */
class FeatureCollector(private val context: Context) {

    // Typing metrics
    private val keyPressTimestamps = mutableListOf<Long>()
    private val keyHoldDurations = mutableListOf<Long>()
    private val typingSpeedSamples = mutableListOf<Float>()
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

    // Movement / accelerometer metrics
    private val accelMagnitudes = mutableListOf<Float>()

    // Step counter
    private var dailyStepCount = 0f

    // Screen unlock count (set externally by service)
    private var screenUnlockCount = 0

    // Usage stats (set externally)
    private var appSwitchCountPerHour = 0f
    private var totalScreenTimeMinutes = 0f
    private var socialAppTimeRatio = 0f
    private var lateNightUsageMinutes = 0f

    // Communication delta (set externally)
    private var callSmsFrequencyDelta = 0f

    companion object {
        private const val FEATURE_WINDOW_MS = 60000L // 1-minute window
        const val NUM_FEATURES = 11

        // Normalization constants (from training data statistics)
        // These map to the means/stds from the training pipeline
        val FEATURE_MEANS = floatArrayOf(
            5.877251f,   // typing_speed_avg
            1.364364f,   // typing_speed_variance
            0.165610f,   // backspace_rate
            12.307791f,  // app_switch_count_per_hour
            5.647415f,   // screen_unlock_count
            28.831104f,  // total_screen_time
            0.415505f,   // social_app_time_ratio
            25.407377f,  // late_night_usage_minutes
            0.787518f,   // movement_variance
            5.685288f,   // step_count_daily
            -0.700154f   // call_sms_frequency_delta
        )
        val FEATURE_STDS = floatArrayOf(
            1.353449f,   // typing_speed_avg
            0.734844f,   // typing_speed_variance
            0.078197f,   // backspace_rate
            6.332695f,   // app_switch_count_per_hour
            2.653187f,   // screen_unlock_count
            9.611197f,   // total_screen_time
            0.137383f,   // social_app_time_ratio
            20.641841f,  // late_night_usage_minutes
            0.448491f,   // movement_variance
            2.026003f,   // step_count_daily
            0.654284f    // call_sms_frequency_delta
        )
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

        // Record instantaneous typing speed sample
        if (keyPressTimestamps.size >= 2) {
            val last2 = keyPressTimestamps.takeLast(2)
            val gap = last2[1] - last2[0]
            if (gap > 0) {
                typingSpeedSamples.add(1000f / gap) // chars per second
            }
        }

        pruneOldData()
    }

    fun onKeyUp(keyCode: Int) {
        val now = System.currentTimeMillis()
        if (lastKeyDownTime > 0) {
            keyHoldDurations.add(now - lastKeyDownTime)
        }
    }

    /** Average typing speed in chars/sec */
    fun getTypingSpeed(): Float {
        if (keyPressTimestamps.size < 2) return 0f
        val timeSpan = keyPressTimestamps.last() - keyPressTimestamps.first()
        if (timeSpan <= 0) return 0f
        return (keyPressTimestamps.size.toFloat() / timeSpan) * 1000f
    }

    /** Variance of typing speed samples */
    fun getTypingSpeedVariance(): Float {
        if (typingSpeedSamples.size < 2) return 0f
        val mean = typingSpeedSamples.average()
        return typingSpeedSamples.map { (it - mean) * (it - mean) }.average().toFloat()
    }

    /** Ratio of backspaces to total key presses */
    fun getBackspaceRatio(): Float {
        if (totalKeyPresses == 0) return 0f
        return backspaceCount.toFloat() / totalKeyPresses
    }

    /** Average key hold duration */
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
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                val distance = sqrt(dx * dx + dy * dy)
                if (duration > 0) {
                    swipeVelocities.add(distance / duration * 1000)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                touchPressures.add(event.pressure)
            }
        }
        pruneOldData()
    }

    fun getAverageTouchPressure(): Float {
        if (touchPressures.isEmpty()) return 0.5f
        return touchPressures.average().toFloat()
    }

    fun getAverageSwipeVelocity(): Float {
        if (swipeVelocities.isEmpty()) return 0f
        return swipeVelocities.average().toFloat()
    }

    // ===================== SESSION METRICS =====================

    fun getSessionLength(): Float {
        return (System.currentTimeMillis() - sessionStartTime) / 1000f
    }

    fun resetSession() {
        sessionStartTime = System.currentTimeMillis()
    }

    // ===================== EXTERNAL DATA SETTERS =====================

    /** Set accelerometer magnitude readings for movement variance */
    fun addAccelReading(magnitude: Float) {
        accelMagnitudes.add(magnitude)
        if (accelMagnitudes.size > 500) {
            accelMagnitudes.subList(0, 250).clear()
        }
    }

    /** Get movement variance from accelerometer data */
    fun getMovementVariance(): Float {
        if (accelMagnitudes.size < 2) return 0.1f
        val mean = accelMagnitudes.average()
        return accelMagnitudes.map { (it - mean) * (it - mean) }.average().toFloat()
    }

    /** Set daily step count (from step counter sensor) */
    fun setStepCount(steps: Float) {
        dailyStepCount = steps
    }

    /** Set screen unlock count */
    fun setScreenUnlockCount(count: Int) {
        screenUnlockCount = count
    }

    /** Set usage stats from UsageStatsHelper */
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

    /** Set call/SMS frequency delta */
    fun setCallSmsDelta(delta: Float) {
        callSmsFrequencyDelta = delta
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

    // ===================== FEATURE VECTOR =====================

    /**
     * Get the 11-feature vector for the ML model, normalized using
     * training data statistics.
     *
     * Order: [typing_speed_avg, typing_speed_variance, backspace_rate,
     *         app_switch_count_per_hour, screen_unlock_count, total_screen_time,
     *         social_app_time_ratio, late_night_usage_minutes,
     *         movement_variance, step_count_daily, call_sms_frequency_delta]
     */
    fun getFeatureVector(): FloatArray {
        // Use global metrics if recent (< 1 minute old) and available
        val useGlobal = (System.currentTimeMillis() - lastGlobalUpdate) < 60000 && globalTypingSpeed > 0

        val rawTypingSpeed = if (useGlobal) globalTypingSpeed else getTypingSpeed()
        val rawTypingVariance = getTypingSpeedVariance()
        val rawBackspaceRate = if (useGlobal) globalErrorRate else getBackspaceRatio()

        val rawFeatures = floatArrayOf(
            rawTypingSpeed,
            rawTypingVariance,
            rawBackspaceRate,
            appSwitchCountPerHour,
            screenUnlockCount.toFloat(),
            totalScreenTimeMinutes,
            socialAppTimeRatio,
            lateNightUsageMinutes,
            getMovementVariance(),
            dailyStepCount / 1000f,  // Scale to match training data
            callSmsFrequencyDelta
        )

        // Normalize: z-score using training data stats
        val normalized = FloatArray(NUM_FEATURES)
        for (i in 0 until NUM_FEATURES) {
            val std = FEATURE_STDS[i]
            normalized[i] = if (std > 0.0001f) {
                (rawFeatures[i] - FEATURE_MEANS[i]) / std
            } else {
                0f
            }
        }

        return normalized
    }

    /**
     * Get raw (un-normalized) feature values for display purposes.
     */
    fun getRawFeatures(): FloatArray {
        val useGlobal = (System.currentTimeMillis() - lastGlobalUpdate) < 60000 && globalTypingSpeed > 0
        return floatArrayOf(
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
            "Swipe Speed" to "%.0f px/sec".format(getAverageSwipeVelocity()),
            "App Switches" to "%.0f/hr".format(appSwitchCountPerHour),
            "Movement" to "%.2f".format(getMovementVariance()),
            "Steps Today" to "%.0f".format(dailyStepCount)
        )
    }

    // ===================== UTILITIES =====================

    private fun pruneOldData() {
        val cutoff = System.currentTimeMillis() - FEATURE_WINDOW_MS
        keyPressTimestamps.removeAll { it < cutoff }

        if (touchPressures.size > 500) {
            touchPressures.subList(0, 250).clear()
        }
        if (swipeVelocities.size > 100) {
            swipeVelocities.subList(0, 50).clear()
        }
        if (typingSpeedSamples.size > 200) {
            typingSpeedSamples.subList(0, 100).clear()
        }
    }

    fun clear() {
        keyPressTimestamps.clear()
        keyHoldDurations.clear()
        typingSpeedSamples.clear()
        touchPressures.clear()
        touchDurations.clear()
        swipeVelocities.clear()
        accelMagnitudes.clear()
        backspaceCount = 0
        totalKeyPresses = 0
        screenUnlockCount = 0
        dailyStepCount = 0f
        sessionStartTime = System.currentTimeMillis()
    }
}
