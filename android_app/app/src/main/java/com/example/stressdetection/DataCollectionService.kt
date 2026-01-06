package com.example.stressdetection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class DataCollectionService : Service(), SensorEventListener {

    private lateinit var usageStatsHelper: UsageStatsHelper
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private var isServiceRunning = false
    
    // Agitation metrics
    private var lastShakeTime: Long = 0
    private var shakeCount = 0
    private var currentAgitation = 0f
    
    private val collectionRunnable = object : Runnable {
        override fun run() {
            calculateBackgroundStress()
            handler.postDelayed(this, UPDATE_INTERVAL)
        }
    }

    companion object {
        private const val TAG = "StressService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "StressDetectionChannel"
        private const val UPDATE_INTERVAL = 30000L // Update every 30 seconds
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        usageStatsHelper = UsageStatsHelper(this)
        
        // Init Sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        
        if (!isServiceRunning) {
            isServiceRunning = true
            startForeground(NOTIFICATION_ID, createNotification("Monitoring usage & movement..."))
            handler.post(collectionRunnable)
            
            // Register sensor
            accelerometer?.also { 
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        
        return START_STICKY
    }

    private fun calculateBackgroundStress() {
        try {
            var stressScore = 0
            
            // Debug values
            val hasPerm = usageStatsHelper.hasPermission()
            val permStatus = if (hasPerm) "Y" else "N"
            
            // 1. Agitation (Shakes)
            // Lowered threshold for testing: 5 shakes = max score
            val agitationScore = (shakeCount * 10).coerceAtMost(50)
            val currentShakes = shakeCount
            shakeCount = 0 // Reset for next window
            
            // 2. Usage Intensity (App Switches)
            var switchRate = 0f
            if (hasPerm) {
                switchRate = usageStatsHelper.getAppSwitchRate()
                // >10 switches/hr is high
                val usageScore = (switchRate * 4).toInt().coerceAtMost(50)
                stressScore = agitationScore + usageScore
            } else {
                // Determine score purely on movement if no permission
                stressScore = agitationScore
            }
            
            stressScore = stressScore.coerceIn(0, 100)
            
            val details = "Score: $stressScore | Perm: $permStatus | Mv: $currentShakes | Sw: ${switchRate.toInt()}"
            
            // Broadcast score to Activity
            val intent = Intent("com.example.stressdetection.UPDATE_STRESS")
            intent.putExtra("score", stressScore)
            intent.putExtra("details", details)
            sendBroadcast(intent)
            
            updateNotification(details)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in calculation: ${e.message}")
            updateNotification("Error: ${e.message}")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
            // Lower threshold to 1.1f for easier testing
            if (gForce > 1.1f) { 
                val now = System.currentTimeMillis()
                if (now - lastShakeTime > 300) { // Faster shakes allowed
                    shakeCount++
                    lastShakeTime = now
                }
            }
        }
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StressDetect Active")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Stress Monitor"
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        handler.removeCallbacks(collectionRunnable)
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Service Stopped")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

