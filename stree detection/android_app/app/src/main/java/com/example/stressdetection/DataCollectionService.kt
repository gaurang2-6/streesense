package com.example.stressdetection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import java.util.Calendar
import kotlin.math.sqrt

/**
 * Background foreground service that collects sensor data and usage stats.
 * Feeds data to FeatureCollector for the 11-feature ML pipeline.
 */
class DataCollectionService : Service(), SensorEventListener {

    private lateinit var usageStatsHelper: UsageStatsHelper
    private lateinit var communicationHelper: CommunicationHelper
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var stepCounter: Sensor? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isServiceRunning = false

    // Accelerometer metrics
    private val recentAccelMagnitudes = mutableListOf<Float>()
    private var lastShakeTime: Long = 0
    private var shakeCount = 0

    // Step counter
    private var initialStepCount = -1f
    private var currentStepCount = 0f

    // Screen unlock tracking
    private var screenUnlockCount = 0
    private var screenOnCount = 0
    private var screenOnTimestamp = 0L
    private var totalScreenOnMs = 0L

    // Feature collector reference (shared via singleton pattern)
    private var featureCollector: FeatureCollector? = null

    // Screen state receiver
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    screenOnCount++
                    screenOnTimestamp = System.currentTimeMillis()
                    Log.d(TAG, "Screen ON ($screenOnCount)")
                }
                Intent.ACTION_SCREEN_OFF -> {
                    if (screenOnTimestamp > 0) {
                        totalScreenOnMs += System.currentTimeMillis() - screenOnTimestamp
                        screenOnTimestamp = 0L
                    }
                    Log.d(TAG, "Screen OFF")
                }
                Intent.ACTION_USER_PRESENT -> {
                    screenUnlockCount++
                    Log.d(TAG, "User UNLOCKED ($screenUnlockCount)")
                }
            }
        }
    }

    private val collectionRunnable = object : Runnable {
        override fun run() {
            collectAndBroadcast()
            handler.postDelayed(this, UPDATE_INTERVAL)
        }
    }

    companion object {
        private const val TAG = "StressService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "StressDetectionChannel"
        private const val UPDATE_INTERVAL = 30000L // 30 seconds

        // Shared FeatureCollector instance for cross-component access
        @Volatile
        var sharedFeatureCollector: FeatureCollector? = null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        usageStatsHelper = UsageStatsHelper(this)
        communicationHelper = CommunicationHelper(this)

        // Use shared collector if available, else create new
        featureCollector = sharedFeatureCollector ?: FeatureCollector(this)

        // Init sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        createNotificationChannel()
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")

        if (!isServiceRunning) {
            isServiceRunning = true
            startForeground(NOTIFICATION_ID, createNotification("Monitoring stress signals..."))
            handler.post(collectionRunnable)

            // Register accelerometer (low frequency to save battery)
            accelerometer?.also {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            // Register step counter
            stepCounter?.also {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d(TAG, "Step counter registered")
            } ?: Log.w(TAG, "No step counter sensor available")
        }

        return START_STICKY
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

    /**
     * Collect all data sources, update the feature collector, compute stress,
     * and broadcast results to the activity.
     */
    private fun collectAndBroadcast() {
        try {
            // 1. Update movement variance from accelerometer
            val movementVar = calculateMovementVariance()
            featureCollector?.addAccelReading(movementVar)

            // 2. Update step count
            featureCollector?.setStepCount(currentStepCount)

            // 3. Update screen unlock count
            featureCollector?.setScreenUnlockCount(screenUnlockCount)

            // 4. Update usage stats
            if (usageStatsHelper.hasPermission()) {
                val switchRate = usageStatsHelper.getAppSwitchRate()
                val screenTimeMins = usageStatsHelper.getHourlyScreenTime()
                val categoryUsage = usageStatsHelper.getCategoryUsage(1)
                val totalMins = categoryUsage.values.sum().toFloat()
                val socialMins = (categoryUsage["Social"] ?: 0).toFloat()
                val socialRatio = if (totalMins > 0) socialMins / totalMins else 0f

                // Late night usage (11pm - 4am)
                val lateNightMins = calculateLateNightUsage()

                featureCollector?.setUsageStats(
                    appSwitches = switchRate,
                    screenTime = screenTimeMins,
                    socialRatio = socialRatio,
                    lateNightMinutes = lateNightMins
                )
            }

            // 5. Update call/SMS delta
            if (communicationHelper.hasPermissions()) {
                val delta = calculateCallSmsDelta()
                featureCollector?.setCallSmsDelta(delta)
            }

            // 6. Run inference using FeatureCollector's feature vector
            val features = featureCollector?.getFeatureVector() ?: FloatArray(11)
            val stressInference = StressInference(this)
            val result = stressInference.analyze(features)
            stressInference.close()

            val stressScore = result.score

            // Build status text
            val status = buildString {
                append("Stress: ${result.level.displayName} ($stressScore)")
                append(" | Steps: ${currentStepCount.toInt()}")
                append(" | Unlocks: $screenUnlockCount")
                append(" | Shakes: $shakeCount")
            }

            // Broadcast score to Activity
            val broadcastIntent = Intent("com.example.stressdetection.UPDATE_STRESS")
            broadcastIntent.putExtra("score", stressScore)
            broadcastIntent.putExtra("confidence", result.confidence)
            broadcastIntent.putExtra("details", status)
            sendBroadcast(broadcastIntent)

            updateNotification(status)

            // Reset shake count for next window
            shakeCount = 0

        } catch (e: Exception) {
            Log.e(TAG, "Error in collection: ${e.message}", e)
            updateNotification("Error: ${e.message}")
        }
    }

    /**
     * Calculate movement variance from recent accelerometer readings.
     */
    private fun calculateMovementVariance(): Float {
        if (recentAccelMagnitudes.size < 2) return 0.1f
        val mean = recentAccelMagnitudes.average()
        val variance = recentAccelMagnitudes.map { (it - mean) * (it - mean) }.average().toFloat()
        recentAccelMagnitudes.clear()
        return variance
    }

    /**
     * Estimate late-night usage (11pm-4am) from screen-on data.
     */
    private fun calculateLateNightUsage(): Float {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        // If currently in late-night window, total screen-on counts
        return if (hour >= 23 || hour < 4) {
            (totalScreenOnMs / 60000f).coerceAtMost(300f)
        } else {
            0f
        }
    }

    /**
     * Calculate call/SMS frequency delta vs a 7-day baseline.
     * Returns a z-score-like value: negative = less social, positive = more social.
     */
    private fun calculateCallSmsDelta(): Float {
        return try {
            val todayCalls = communicationHelper.getCallFrequency(1).values.sum()
            val weekCalls = communicationHelper.getCallFrequency(7).values.sum()
            val avgDailyCalls = weekCalls / 7f
            if (avgDailyCalls > 0) {
                (todayCalls - avgDailyCalls) / avgDailyCalls.coerceAtLeast(1f)
            } else {
                0f
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating call delta: ${e.message}")
            0f
        }
    }

    // =================== SENSOR CALLBACKS ===================

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt(x * x + y * y + z * z)

                recentAccelMagnitudes.add(magnitude)
                if (recentAccelMagnitudes.size > 200) {
                    recentAccelMagnitudes.subList(0, 100).clear()
                }

                // Detect shakes for agitation metric
                val gForce = magnitude / SensorManager.GRAVITY_EARTH
                if (gForce > 1.1f) {
                    val now = System.currentTimeMillis()
                    if (now - lastShakeTime > 300) {
                        shakeCount++
                        lastShakeTime = now
                    }
                }
            }

            Sensor.TYPE_STEP_COUNTER -> {
                val steps = event.values[0]
                if (initialStepCount < 0) {
                    initialStepCount = steps
                }
                currentStepCount = steps - initialStepCount
                Log.d(TAG, "Steps today: ${currentStepCount.toInt()}")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // =================== NOTIFICATION ===================

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Stress Monitor"
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    // =================== LIFECYCLE ===================

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        handler.removeCallbacks(collectionRunnable)
        sensorManager.unregisterListener(this)
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Screen receiver already unregistered")
        }
        Log.d(TAG, "Service Stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
