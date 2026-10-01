package com.stresssense.app.domain.behavioral

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Calendar

/**
 * Reads call-log and SMS history via system ContentProviders to support the
 * social-pattern feature (call/SMS frequency delta vs baseline).
 *
 * Degrades gracefully (returns empty maps) if permissions are not granted.
 * Ported from StressDetect, re-namespaced to StressSense.
 */
class CommunicationHelper(private val context: Context) {

    companion object {
        private const val TAG = "StressComm"
    }

    fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED
    }

    /** Map<dayStartMillis, callCount> over the last [days] days. */
    fun getCallFrequency(days: Int = 7): Map<Long, Int> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) !=
            PackageManager.PERMISSION_GRANTED
        ) return emptyMap()

        val result = mutableMapOf<Long, Int>()
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }
        val startTime = calendar.timeInMillis

        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.DATE),
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(startTime.toString()),
                "${CallLog.Calls.DATE} ASC"
            )?.use { cursor ->
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                if (dateIndex == -1) return emptyMap()
                while (cursor.moveToNext()) {
                    val timestamp = cursor.getLong(dateIndex)
                    val dayTime = normalizeDate(timestamp)
                    result[dayTime] = result.getOrDefault(dayTime, 0) + 1
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading call logs: ${e.message}")
        }
        return result
    }

    /** Map<dayStartMillis, smsCount> over the last [days] days. */
    fun getSmsFrequency(days: Int = 7): Map<Long, Int> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) return emptyMap()

        val result = mutableMapOf<Long, Int>()
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }
        val startTime = calendar.timeInMillis

        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(startTime.toString()),
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
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
