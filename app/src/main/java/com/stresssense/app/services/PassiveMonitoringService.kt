package com.stresssense.app.services

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.stresssense.app.MainActivity
import com.stresssense.app.data.models.PassiveMetric
import com.stresssense.app.data.repository.StressRepository
import com.stresssense.app.domain.behavioral.BehavioralFeatureCollector
import com.stresssense.app.domain.behavioral.CommunicationHelper
import com.stresssense.app.domain.behavioral.StressInferenceEngine
import com.stresssense.app.domain.behavioral.UsageStatsHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * Foreground service for continuous passive stress monitoring.
 *
 * Combines the original accelerometer micro-tremor detection with the
 * interaction-based behavioral pipeline (ported from StressDetect):
 *  - Accelerometer micro-tremors (motion stress)
 *  - Step counter (daily activity)
 *  - Usage stats (app switching, screen time, social ratio, late-night use)
 *  - Call/SMS frequency delta vs baseline
 *  - On-device TFLite behavioral stress inference (11-feature model)
 *
 * Every [ANALYSIS_WINDOW_MS] it assembles the feature vector, runs ML
 * inference, and writes a PassiveMetric with the motion score plus the
 * ML-derived typing/usage scores.
 *
 * Battery-conscious: only runs while passive monitoring is enabled.
 */
@AndroidEntryPoint
class PassiveMonitoringService : Service(), SensorEventListener {

    @Inject lateinit var repository: StressRepository
    @Inject lateinit var featureCollector: BehavioralFeatureCollector
    @Inject lateinit var inferenceEngine: StressInferenceEngine
    @Inject lateinit var usageStatsHelper: UsageStatsHelper
    @Inject lateinit var communicationHelper: CommunicationHelper

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var stepCounter: Sensor? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Accelerometer data collection
    private val accelerometerReadings = mutableListOf<Float>()
    private val readingTimestamps = mutableListOf<Long>()
    private var lastReadingTime = 0L

    // Step counter baseline (set on first event)
    private var initialStepCount = -1
    private var currentStepCount = 0f

    // Screen tracking
    private var screenOnCount = 0
    private var screenUnlockCount = 0

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> screenOnCount++
                Intent.ACTION_USER_PRESENT -> screenUnlockCount++
            }
        }
    }

    /** Receives global typing metrics broadcast by TypingAccessibilityService. */
    private val typingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != TypingAccessibilityService.ACTION_UPDATE_TYPING) return
            val speed = intent.getFloatExtra(TypingAccessibilityService.EXTRA_SPEED, 0f)
            val errorRate = intent.getFloatExtra(TypingAccessibilityService.EXTRA_ERROR_RATE, 0f)
            val scrollRate = intent.getFloatExtra(TypingAccessibilityService.EXTRA_SCROLL_RATE, 0f)
            featureCollector.setGlobalMetrics(speed, errorRate, scrollRate)
        }
    }

    companion object {
        const val CHANNEL_ID = "passive_monitoring"
        const val NOTIFICATION_ID = 1001
        const val SAMPLING_INTERVAL_MS = 20L  // 50Hz
        const val ANALYSIS_WINDOW_MS = 30_000L // Analyze every 30 seconds
        private const val TAG = "PassiveMonitoring"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // On Android 14 (targetSdk 34), when the manifest declares a
        // foregroundServiceType, the 3-arg startForeground MUST be used —
        // the 2-arg version throws MissingForegroundServiceTypeException and
        // crashes the service. We also wrap it so any foreground-start
        // failure stops the service cleanly instead of crashing the app.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Start accelerometer listening
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        // Step counter (cumulative since boot)
        stepCounter?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Track screen on / unlock events
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
        } catch (_: Exception) {
            // Receiver registration can fail on heavily restricted devices.
        }

        // Receive global typing metrics from the accessibility service
        try {
            val filter = IntentFilter(TypingAccessibilityService.ACTION_UPDATE_TYPING)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(typingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(typingReceiver, filter)
            }
        } catch (_: Exception) {
            // Ignore — feature collector simply won't get global typing data.
        }

        // Periodic analysis
        serviceScope.launch {
            while (isActive) {
                delay(ANALYSIS_WINDOW_MS)
                analyzeAndSave()
            }
        }

        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val now = System.currentTimeMillis()
                if (now - lastReadingTime < SAMPLING_INTERVAL_MS) return
                lastReadingTime = now

                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt(x * x + y * y + z * z)

                synchronized(accelerometerReadings) {
                    accelerometerReadings.add(magnitude)
                    readingTimestamps.add(now)
                }
                // Feed the behavioral pipeline too
                featureCollector.addAccelReading(magnitude)
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val total = event.values[0]
                if (initialStepCount < 0) initialStepCount = total.toInt()
                currentStepCount = (total.toInt() - initialStepCount).toFloat()
                featureCollector.setStepCount(currentStepCount)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Assemble features, run inference, and persist a PassiveMetric.
     */
    private suspend fun analyzeAndSave() {
        // 1. Motion score (from the original accelerometer pipeline)
        val readings: List<Float>
        synchronized(accelerometerReadings) {
            readings = accelerometerReadings.toList()
            accelerometerReadings.clear()
            readingTimestamps.clear()
        }
        val motionScore = if (readings.size >= 50) computeMotionStressScore(readings) else null

        // 2. Push usage / step / unlock signals into the feature collector
        featureCollector.setScreenUnlockCount(screenUnlockCount)
        featureCollector.setStepCount(currentStepCount)

        var usageScore: Int? = null
        try {
            if (usageStatsHelper.hasPermission()) {
                val appSwitches = usageStatsHelper.getAppSwitchRate()
                val screenTime = usageStatsHelper.getHourlyScreenTime()
                val socialRatio = usageStatsHelper.getSocialAppTimeRatio()
                val lateNight = usageStatsHelper.calculateLateNightUsage()
                featureCollector.setUsageStats(appSwitches, screenTime, socialRatio, lateNight)

                // Heuristic usage stress indicator (0-1) -> 0-100 score.
                val avgSession = usageStatsHelper.getAverageSessionLength()
                var indicator = 0f
                if (appSwitches > 10) indicator += 0.3f        // distracted switching
                if (avgSession in 1f..30f) indicator += 0.3f   // very short sessions
                if (usageStatsHelper.isNightUsage()) indicator += 0.2f
                if (screenTime > 45) indicator += 0.2f          // heavy recent use
                usageScore = (indicator * 100).toInt().coerceIn(0, 100)
            }
        } catch (_: Exception) {
            // Usage stats can throw SecurityException if permission is revoked.
        }

        // 3. Call/SMS delta (optional)
        try {
            if (communicationHelper.hasPermissions()) {
                val callFreq = communicationHelper.getCallFrequency(7)
                val delta = computeCallSmsDelta(callFreq)
                featureCollector.setCallSmsDelta(delta)
            }
        } catch (_: Exception) {
            // Communication reads can fail; pipeline continues without it.
        }

        // 4. Run ML inference once on the assembled feature vector.
        var mlScore: Int? = null
        var typingScore: Int? = null
        try {
            val features = featureCollector.getFeatureVector()
            val result = inferenceEngine.analyze(features)
            mlScore = result.score
            // If we have global typing data from the accessibility service,
            // the ML score is a good proxy for typing-related stress.
            if (featureCollector.hasGlobalTypingData()) typingScore = result.score
        } catch (e: Exception) {
            Log.w(TAG, "Inference failed: ${e.message}")
        }

        // 5. Persist the metric. Each field is null-safe so partial data
        //    (e.g. no usage permission) still produces a useful row.
        val metric = PassiveMetric(
            timestamp = System.currentTimeMillis(),
            typingScore = typingScore,
            usageScore = usageScore,
            motionScore = motionScore,
            mlStressScore = mlScore
        )
        repository.savePassiveMetric(metric)
    }

    /**
     * Z-score-like delta of today's call count vs the 7-day daily average.
     */
    private fun computeCallSmsDelta(freq: Map<Long, Int>): Float {
        if (freq.isEmpty()) return 0f
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val today = cal.timeInMillis
        val todayCount = freq[today] ?: 0
        val priorDays = freq.keys.filter { it != today }.mapNotNull { freq[it] }
        val avg = if (priorDays.isNotEmpty()) priorDays.average() else 0.0
        val std = if (priorDays.size > 1) {
            kotlin.math.sqrt(priorDays.map { (it - avg) * (it - avg) }.average())
        } else 1.0
        return if (std > 0.01) ((todayCount - avg) / std).toFloat() else 0f
    }

    /**
     * Compute motion stress score from accelerometer jitter.
     */
    private fun computeMotionStressScore(readings: List<Float>): Int {
        if (readings.size < 10) return 30
        val gravity = 9.81f
        val deviations = readings.map { (it - gravity) }
        val mean = deviations.average()
        val variance = deviations.map { (it - mean) * (it - mean) }.average()
        return when {
            variance > 2.0 -> 80
            variance > 1.0 -> 65
            variance > 0.5 -> 50
            variance > 0.2 -> 35
            else -> 15
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Passive Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors stress signals in the background"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StressSense")
            .setContentText("Monitoring stress signals")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(typingReceiver) } catch (_: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }
}
