package com.stresssense.app.domain.behavioral

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import java.util.Calendar

/**
 * Wraps [UsageStatsManager] to derive app-switch rate, screen time, category
 * breakdowns, and night-usage signals. Requires the PACKAGE_USAGE_STATS
 * special permission (granted via Settings).
 *
 * Ported from StressDetect, re-namespaced to StressSense.
 */
class UsageStatsHelper(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    companion object {
        val SOCIAL_APPS = setOf(
            "com.facebook.katana", "com.instagram.android", "com.twitter.android",
            "com.snapchat.android", "com.whatsapp", "com.zhiliaoapp.musically"
        )
        val PRODUCTIVITY_APPS = setOf(
            "com.google.android.gm", "com.microsoft.office.word",
            "com.google.android.docs", "com.slack", "com.zoom.videomeetings"
        )
        val ENTERTAINMENT_APPS = setOf(
            "com.netflix.mediaclient", "com.google.android.youtube",
            "com.spotify.music", "com.amazon.avod.thirdpartyclient"
        )
    }

    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /** App switches (distinct package foreground transitions) in the last hour. */
    fun getAppSwitchRate(): Float {
        if (!hasPermission()) return 0f
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 60 * 60 * 1000L
        val events = usageStatsManager.queryEvents(startTime, endTime)
        var switches = 0
        var lastPackage = ""
        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (event.packageName != lastPackage) {
                    switches++
                    lastPackage = event.packageName
                }
            }
        }
        return switches.toFloat()
    }

    /** Rough proxy for foreground minutes in the last hour. */
    fun getHourlyScreenTime(): Float {
        if (!hasPermission()) return 0f
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 60 * 60 * 1000L
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )
        var totalTime = 0L
        for (usage in stats) totalTime += usage.totalTimeInForeground
        return totalTime / 1000f / 60f
    }

    /** Usage minutes per category over the last [days] days. */
    fun getCategoryUsage(days: Int = 7): Map<String, Long> {
        if (!hasPermission()) return emptyMap()
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (days * 24 * 60 * 60 * 1000L)
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )
        var socialTime = 0L
        var productivityTime = 0L
        var entertainmentTime = 0L
        for (usage in stats) {
            val pkg = usage.packageName
            val time = usage.totalTimeInForeground
            when {
                SOCIAL_APPS.contains(pkg) -> socialTime += time
                PRODUCTIVITY_APPS.contains(pkg) -> productivityTime += time
                ENTERTAINMENT_APPS.contains(pkg) -> entertainmentTime += time
            }
        }
        return mapOf(
            "Social" to socialTime / 1000 / 60,
            "Productivity" to productivityTime / 1000 / 60,
            "Entertainment" to entertainmentTime / 1000 / 60
        )
    }

    /** Average foreground session length (seconds) over the last 24h. */
    fun getAverageSessionLength(): Float {
        if (!hasPermission()) return 0f
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 24 * 60 * 60 * 1000L
        val events = usageStatsManager.queryEvents(startTime, endTime)
        var sessions = 0
        var totalDuration = 0L
        var lastForegroundTime = 0L
        var isForeground = false
        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForegroundTime = event.timeStamp
                isForeground = true
                sessions++
            } else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND && isForeground) {
                totalDuration += (event.timeStamp - lastForegroundTime)
                isForeground = false
            }
        }
        return if (sessions > 0) (totalDuration / sessions) / 1000f else 0f
    }

    fun isNightUsage(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour < 6
    }

    /** Screen-on minutes between 23:00-04:00 today (approx, from last-hour proxy). */
    fun calculateLateNightUsage(): Float {
        return if (isNightUsage()) getHourlyScreenTime() else 0f
    }

    /** Social-app time ratio (0-1) for the last day. */
    fun getSocialAppTimeRatio(): Float {
        val categories = getCategoryUsage(1)
        val social = categories["Social"]?.toFloat() ?: 0f
        val total = categories.values.sum().toFloat()
        return if (total > 0) social / total else 0f
    }
}
