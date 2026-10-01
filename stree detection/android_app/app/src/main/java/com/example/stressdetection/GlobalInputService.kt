package com.example.stressdetection

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class GlobalInputService : AccessibilityService() {

    private var charCount = 0
    private var errorCount = 0
    private var clickCount = 0
    private var scrollCount = 0
    private var lastUpdate = System.currentTimeMillis()
    private var startTime = System.currentTimeMillis()

    companion object {
        const val ACTION_UPDATE_TYPING = "com.example.stressdetection.UPDATE_TYPING"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val added = event.addedCount
                val removed = event.removedCount
                if (added > 0) charCount += added
                if (removed > 0) errorCount += removed
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                clickCount++
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                scrollCount++
            }
        }

        val now = System.currentTimeMillis()
        if (now - lastUpdate > 1000) { // Update every second if active
            broadcastMetrics()
            lastUpdate = now
        }
    }

    private fun broadcastMetrics() {
        val now = System.currentTimeMillis()
        val duration = (now - startTime) / 1000f // seconds
        
        if (duration < 1) return

        // Calculate rates
        val speed = charCount / duration
        val errorRate = if (charCount > 0) errorCount.toFloat() / (charCount + errorCount) else 0f
        val clickRate = clickCount / duration
        val scrollRate = scrollCount / duration

        val intent = Intent(ACTION_UPDATE_TYPING)
        intent.putExtra("speed", speed)
        intent.putExtra("errorRate", errorRate)
        intent.putExtra("clickRate", clickRate)
        intent.putExtra("scrollRate", scrollRate)
        sendBroadcast(intent)
        
        // Reset periodically to keep it "real-time"
        if (duration > 10) {
            charCount = 0
            errorCount = 0
            clickCount = 0
            scrollCount = 0
            startTime = now
        }
    }

    override fun onInterrupt() {
        // Service interrupted
    }
}
