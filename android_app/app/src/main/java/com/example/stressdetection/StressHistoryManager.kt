package com.example.stressdetection

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages historical stress data for trend analysis and visualization.
 * Uses simple JSON serialization to avoid Gson dependency issues.
 */
class StressHistoryManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    companion object {
        private const val TAG = "StressHistory"
        private const val PREFS_NAME = "stress_history"
        private const val KEY_HISTORY = "stress_readings"
        private const val MAX_ENTRIES = 168 // 7 days of hourly readings
    }

    data class StressReading(
        val timestamp: Long,
        val score: Int,
        val level: String,
        val typingSpeed: Float,
        val backspaceRatio: Float,
        val touchPressure: Float
    )

    /**
     * Save a new stress reading
     */
    fun saveReading(result: StressInference.StressResult, features: FloatArray) {
        try {
            val readings = getHistory().toMutableList()
            
            readings.add(
                StressReading(
                    timestamp = System.currentTimeMillis(),
                    score = result.score,
                    level = result.level.displayName,
                    typingSpeed = features.getOrElse(0) { 0f },
                    backspaceRatio = features.getOrElse(1) { 0f },
                    touchPressure = features.getOrElse(2) { 0f }
                )
            )
            
            // Keep only recent entries
            while (readings.size > MAX_ENTRIES) {
                readings.removeAt(0)
            }
            
            // Serialize to JSON
            val jsonArray = JSONArray()
            for (reading in readings) {
                val obj = JSONObject()
                obj.put("timestamp", reading.timestamp)
                obj.put("score", reading.score)
                obj.put("level", reading.level)
                obj.put("typingSpeed", reading.typingSpeed.toDouble())
                obj.put("backspaceRatio", reading.backspaceRatio.toDouble())
                obj.put("touchPressure", reading.touchPressure.toDouble())
                jsonArray.put(obj)
            }
            
            prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
            Log.d(TAG, "Saved reading: score=${result.score}, total readings=${readings.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving reading: ${e.message}")
        }
    }

    /**
     * Get all historical readings
     */
    fun getHistory(): List<StressReading> {
        return try {
            val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
            val jsonArray = JSONArray(json)
            val readings = mutableListOf<StressReading>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                readings.add(
                    StressReading(
                        timestamp = obj.getLong("timestamp"),
                        score = obj.getInt("score"),
                        level = obj.getString("level"),
                        typingSpeed = obj.getDouble("typingSpeed").toFloat(),
                        backspaceRatio = obj.getDouble("backspaceRatio").toFloat(),
                        touchPressure = obj.getDouble("touchPressure").toFloat()
                    )
                )
            }
            readings
        } catch (e: Exception) {
            Log.e(TAG, "Error reading history: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get readings for today
     */
    fun getTodayReadings(): List<StressReading> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfDay = calendar.timeInMillis
        
        return getHistory().filter { it.timestamp >= startOfDay }
    }

    /**
     * Get average stress score for today
     */
    fun getTodayAverageScore(): Int {
        val readings = getTodayReadings()
        if (readings.isEmpty()) return 0
        return readings.map { it.score }.average().toInt()
    }

    /**
     * Get hourly average scores for chart display
     */
    fun getHourlyAverages(): List<Pair<Int, Int>> {
        val readings = getTodayReadings()
        return readings.groupBy { reading ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = reading.timestamp
            cal.get(Calendar.HOUR_OF_DAY)
        }.map { (hour, hourReadings) ->
            hour to hourReadings.map { it.score }.average().toInt()
        }.sortedBy { it.first }
    }

    /**
     * Get trend direction (improving, stable, worsening)
     */
    fun getTrend(): Trend {
        val readings = getHistory()
        if (readings.size < 2) return Trend.STABLE
        
        // Compare recent average to older average
        val midpoint = readings.size / 2
        val olderAvg = readings.take(midpoint).map { it.score }.average()
        val newerAvg = readings.drop(midpoint).map { it.score }.average()
        
        val diff = newerAvg - olderAvg
        return when {
            diff < -10 -> Trend.IMPROVING
            diff > 10 -> Trend.WORSENING
            else -> Trend.STABLE
        }
    }

    /**
     * Clear all history
     */
    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    /**
     * Generate demo data for the past 24 hours
     */
    fun generateDemoData() {
        val readings = mutableListOf<StressReading>()
        val calendar = Calendar.getInstance()
        val currentTime = calendar.timeInMillis
        
        // Go back 24 hours
        calendar.add(Calendar.HOUR_OF_DAY, -24)
        
        while (calendar.timeInMillis <= currentTime) {
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val time = calendar.timeInMillis
            
            // Generate purely random stress value as requested
            val score = (Math.random() * 100).toInt()
            
            val level = when {
                score <= 30 -> "RELAXED"
                score <= 65 -> "MODERATE"
                else -> "STRESSED"
            }
            
            readings.add(
                StressReading(
                    timestamp = time,
                    score = score,
                    level = level,
                    typingSpeed = (Math.random() * 10).toFloat(),
                    backspaceRatio = (Math.random() * 0.3).toFloat(),
                    touchPressure = (Math.random() * 1.0).toFloat()
                )
            )
            
            // Frequent updates for a dense graph (every 30 mins)
            calendar.add(Calendar.MINUTE, 30)
        }
        
        // Save batch
        try {
            val jsonArray = JSONArray()
            for (reading in readings) {
                val obj = JSONObject()
                obj.put("timestamp", reading.timestamp)
                obj.put("score", reading.score)
                obj.put("level", reading.level)
                obj.put("typingSpeed", reading.typingSpeed.toDouble())
                obj.put("backspaceRatio", reading.backspaceRatio.toDouble())
                obj.put("touchPressure", reading.touchPressure.toDouble())
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
            Log.d(TAG, "Generated demo data: ${readings.size} entries")
        } catch (e: Exception) {
            Log.e(TAG, "Error generating demo data: ${e.message}")
        }
    }

    enum class Trend(val emoji: String, val description: String) {
        IMPROVING("📉", "Your stress levels are decreasing"),
        STABLE("➡️", "Your stress levels are stable"),
        WORSENING("📈", "Your stress levels are increasing")
    }
}
