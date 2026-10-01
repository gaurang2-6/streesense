package com.stresssense.app.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.stresssense.app.data.models.PassiveMetric
import com.stresssense.app.data.repository.StressRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * AccessibilityService to analyze typing patterns for passive stress detection.
 *
 * Tracks:
 * - Typing speed (characters/min)
 * - Backspace ratio (indicator of frustration/stress)
 * - Inter-key latency variance (rhythm irregularity)
 * - Click & scroll rates (fidgeting / distraction)
 *
 * Periodically broadcasts the aggregated typing metrics (speed, error rate,
 * scroll rate) so the [PassiveMonitoringService] / BehavioralFeatureCollector
 * can fold global typing data into the ML feature vector.
 *
 * CRITICAL: Privacy-first. We do NOT read, log, or transmit any typed text
 * content. Only timing intervals and counts are measured.
 */
@AndroidEntryPoint
class TypingAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var repository: StressRepository

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // Session-based state
    private var lastKeyTime = 0L
    private val keyIntervals = mutableListOf<Long>()
    private var characterCount = 0
    private var backspaceCount = 0
    private var sessionStartTime = 0L

    // Global-rate counters (reset every broadcast window)
    private var windowChars = 0
    private var windowErrors = 0
    private var windowClicks = 0
    private var windowScrolls = 0
    private var windowStartMs = System.currentTimeMillis()

    companion object {
        private const val SESSION_TIMEOUT_MS = 5000L // 5s inactivity ends session
        private const val MIN_CHARACTERS_FOR_ANALYSIS = 10
        private const val BROADCAST_INTERVAL_MS = 2000L

        /** Broadcast action carrying global typing metrics. */
        const val ACTION_UPDATE_TYPING = "com.stresssense.app.UPDATE_TYPING"
        const val EXTRA_SPEED = "speed"          // chars/sec
        const val EXTRA_ERROR_RATE = "errorRate" // 0..1
        const val EXTRA_CLICK_RATE = "clickRate"
        const val EXTRA_SCROLL_RATE = "scrollRate"
    }

    private val broadcastRunnable = object : Runnable {
        override fun run() {
            broadcastGlobalMetrics()
            mainHandler.postDelayed(this, BROADCAST_INTERVAL_MS)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleTextChanged(event)
            AccessibilityEvent.TYPE_VIEW_CLICKED -> windowClicks++
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> windowScrolls++
        }
    }

    private fun handleTextChanged(event: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        if (sessionStartTime == 0L) sessionStartTime = now

        if (lastKeyTime != 0L && (now - lastKeyTime > SESSION_TIMEOUT_MS)) {
            finalizeSession()
            sessionStartTime = now
        }

        val added = event.addedCount.coerceAtLeast(0)
        val removed = event.removedCount.coerceAtLeast(0)

        if (removed > 0) {
            backspaceCount += removed
            windowErrors += removed
        }
        if (added > 0) {
            characterCount += added
            windowChars += added
            if (lastKeyTime != 0L) {
                val interval = now - lastKeyTime
                if (interval < SESSION_TIMEOUT_MS) {
                    synchronized(keyIntervals) { keyIntervals.add(interval) }
                }
            }
        }
        lastKeyTime = now
    }

    /**
     * Broadcast the current typing/click/scroll rates so the foreground
     * service's feature collector can use global typing data.
     */
    private fun broadcastGlobalMetrics() {
        val now = System.currentTimeMillis()
        val durationSec = ((now - windowStartMs) / 1000f).coerceAtLeast(0.1f)
        val speed = windowChars / durationSec                    // chars/sec
        val totalEvents = windowChars + windowErrors
        val errorRate = if (totalEvents > 0) windowErrors.toFloat() / totalEvents else 0f
        val clickRate = windowClicks / durationSec
        val scrollRate = windowScrolls / durationSec

        val intent = Intent(ACTION_UPDATE_TYPING).apply {
            setPackage(packageName) // keep the broadcast private to this app
            putExtra(EXTRA_SPEED, speed)
            putExtra(EXTRA_ERROR_RATE, errorRate)
            putExtra(EXTRA_CLICK_RATE, clickRate)
            putExtra(EXTRA_SCROLL_RATE, scrollRate)
        }
        sendBroadcast(intent)

        // Reset the window
        windowChars = 0
        windowErrors = 0
        windowClicks = 0
        windowScrolls = 0
        windowStartMs = now
    }

    private fun finalizeSession() {
        val totalChars = characterCount + backspaceCount
        if (totalChars < MIN_CHARACTERS_FOR_ANALYSIS) {
            resetSession()
            return
        }

        val sessionDurationMin = (lastKeyTime - sessionStartTime) / 60000.0
        val speed = if (sessionDurationMin > 0.0) {
            (totalChars / sessionDurationMin).toInt()
        } else 0

        val backspaceRatio = if (totalChars > 0) backspaceCount.toFloat() / totalChars else 0f

        val intervals: List<Long>
        synchronized(keyIntervals) { intervals = keyIntervals.toList() }

        val latencyVariance = computeVariance(intervals)
        val score = computeTypingStressScore(speed, backspaceRatio, latencyVariance)

        serviceScope.launch {
            val metric = PassiveMetric(
                timestamp = System.currentTimeMillis(),
                typingScore = score,
                usageScore = null,
                motionScore = null
            )
            repository.savePassiveMetric(metric)
        }

        resetSession()
    }

    private fun computeVariance(list: List<Long>): Double {
        if (list.size < 2) return 0.0
        val mean = list.average()
        return list.map { (it - mean) * (it - mean) }.average()
    }

    private fun computeTypingStressScore(speed: Int, backspaceRatio: Float, variance: Double): Int {
        var score = 30 // Baseline
        if (backspaceRatio > 0.20f) score += 30
        else if (backspaceRatio > 0.10f) score += 15
        if (variance > 10000.0) score += 25
        else if (variance > 4000.0) score += 12
        if (speed > 300) score += 15
        else if (speed < 50 && backspaceRatio > 0.15f) score += 15
        return score.coerceIn(10, 95)
    }

    private fun resetSession() {
        lastKeyTime = 0L
        synchronized(keyIntervals) { keyIntervals.clear() }
        characterCount = 0
        backspaceCount = 0
        sessionStartTime = 0L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        mainHandler.post(broadcastRunnable)
    }

    override fun onInterrupt() {
        finalizeSession()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(broadcastRunnable)
        finalizeSession()
        serviceScope.cancel()
        super.onDestroy()
    }
}
