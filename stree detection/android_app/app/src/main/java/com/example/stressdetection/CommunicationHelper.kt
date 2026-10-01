package com.example.stressdetection

import android.content.Context
import android.provider.CallLog
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

/**
 * Accesses historical communication data (Call Logs, SMS) for social pattern analysis.
 */
class CommunicationHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "StressComm"
    }

    fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get call frequency per day for the last [days] days.
     * Returns a map of Timestamp (start of day) -> Call Count
     */
    fun getCallFrequency(days: Int = 7): Map<Long, Int> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return emptyMap()
        }

        val result = mutableMapOf<Long, Int>()
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -days)
        val startTime = calendar.timeInMillis

        val projection = arrayOf(CallLog.Calls.DATE)
        val selection = "${CallLog.Calls.DATE} >= ?"
        val selectionArgs = arrayOf(startTime.toString())

        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CallLog.Calls.DATE} ASC"
            )?.use { cursor ->
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                if (dateIndex == -1) return emptyMap()

                while (cursor.moveToNext()) {
                    val timestamp = cursor.getLong(dateIndex)
                    // Normalize to start of day
                    val dayTime = normalizeDate(timestamp)
                    result[dayTime] = result.getOrDefault(dayTime, 0) + 1
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading call logs: ${e.message}")
        }
        return result
    }

    /**
     * Get SMS frequency per day for the last [days] days.
     */
    fun getSmsFrequency(days: Int = 7): Map<Long, Int> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return emptyMap()
        }

        val result = mutableMapOf<Long, Int>()
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -days)
        val startTime = calendar.timeInMillis

        val projection = arrayOf(Telephony.Sms.DATE)
        val selection = "${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(startTime.toString())

        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} ASC"
            )?.use { cursor ->
                val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
                if (dateIndex == -1) return emptyMap()

                while (cursor.moveToNext()) {
                    val timestamp = cursor.getLong(dateIndex)
                    val dayTime = normalizeDate(timestamp)
                    result[dayTime] = result.getOrDefault(dayTime, 0) + 1
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading SMS logs: ${e.message}")
        }
        return result
    }

    private fun normalizeDate(timestamp: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
