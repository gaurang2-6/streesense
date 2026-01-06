package com.example.stressdetection

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import android.graphics.Color

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "StressDetect"
    }

    // UI Elements
    private lateinit var tvStressScore: TextView
    private lateinit var tvStressLabel: TextView
    private lateinit var tvTrendEmoji: TextView
    private lateinit var tvTrendText: TextView
    private lateinit var tvTypingSpeed: TextView
    private lateinit var tvErrorRate: TextView
    private lateinit var tvTouchPressure: TextView
    private lateinit var tvInsight: TextView
    private lateinit var tvAppSwitches: TextView
    private lateinit var ivSettings: android.widget.ImageView
    private lateinit var tvScreenTime: TextView
    private lateinit var tvAvgSession: TextView

    private lateinit var lineChart: LineChart
    private lateinit var btnStartTracking: Button
    private lateinit var btnLogMood: Button

    // Core components
    private lateinit var featureCollector: FeatureCollector
    private lateinit var stressInference: StressInference
    private lateinit var usageStatsHelper: UsageStatsHelper
    private lateinit var historyManager: StressHistoryManager
    private lateinit var communicationHelper: CommunicationHelper

    // State
    private var isTracking = false
    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateInterval = 3000L // 3 seconds for more responsive updates

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isTracking) {
                updateStressAnalysis()
                updateHandler.postDelayed(this, updateInterval)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "MainActivity onCreate")
        
        initViews()
        initComponents()
        setupClickListeners()
        setupChart()
        loadInitialData()
        
        // Show initial status
        Toast.makeText(this, "StressDetect Ready", Toast.LENGTH_SHORT).show()
    }

    private fun initViews() {
        tvStressScore = findViewById(R.id.tv_stress_score)
        tvStressLabel = findViewById(R.id.tv_stress_label)
        tvTrendEmoji = findViewById(R.id.tv_trend_emoji)
        tvTrendText = findViewById(R.id.tv_trend_text)
        tvTypingSpeed = findViewById(R.id.tv_typing_speed)
        tvErrorRate = findViewById(R.id.tv_error_rate)
        tvTouchPressure = findViewById(R.id.tv_touch_pressure)
        tvInsight = findViewById(R.id.tv_insight)
        tvAppSwitches = findViewById(R.id.tv_app_switches)
        ivSettings = findViewById(R.id.iv_settings)
        tvScreenTime = findViewById(R.id.tv_screen_time)
        tvAvgSession = findViewById(R.id.tv_avg_session)

        lineChart = findViewById(R.id.chart_line)
        btnStartTracking = findViewById(R.id.btn_start_tracking)
        btnLogMood = findViewById(R.id.btn_log_mood)
        
        // Find or add Survey button dynamically if not in layout yet (or repurpose Log Mood for now)
        // ideally we'd update XML, but for now we can access via ID if it existed, or add logic to existing buttons
        
        Log.d(TAG, "Views initialized")
    }

    private fun initComponents() {
        featureCollector = FeatureCollector(this)
        stressInference = StressInference(this)
        usageStatsHelper = UsageStatsHelper(this)
        historyManager = StressHistoryManager(this)
        communicationHelper = CommunicationHelper(this)
        
        Log.d(TAG, "Components initialized. Model loaded: ${stressInference.isModelLoaded()}")
    }

    private fun setupClickListeners() {
        btnStartTracking.setOnClickListener {
            Log.d(TAG, "Start tracking button clicked. isTracking: $isTracking")
            if (!isTracking) {
                startTracking()
            } else {
                stopTracking()
            }
        }

        btnLogMood.setOnClickListener {
            // Updated to perform dual function: Long press for survey, click for mood
            showMoodDialog()
        }
        
        btnLogMood.setOnLongClickListener {
            startActivity(Intent(this, SurveyActivity::class.java))
            true
        }
        
        // Hidden feature: Tap score to sync historical data
        tvStressScore.setOnClickListener {
            syncHistoricalData()
        }
        
        // Interactive Metrics
        tvTypingSpeed.setOnClickListener { showMetricInfo("Typing Speed", "Normal active typing is 3-6 chars/sec.\n\nNOTE: To track typing in other apps (like WhatsApp), you MUST enable 'StressDetect' in Settings > Accessibility.") }
        tvErrorRate.setOnClickListener { showMetricInfo("Error Rate", "High backspace usage (>10%) suggests stress.\n\nRequires Accessibility permission to work globally.") }
        tvTouchPressure.setOnClickListener { showMetricInfo("Touch Pressure", "Harder screen presses (>0.7) are strongly correlated with high stress levels.") }
    
    // Demo Mode Trigger
    ivSettings.setOnClickListener {
        AlertDialog.Builder(this)
            .setTitle("Demo Mode")
            .setMessage("Regenerate synthetic demo data for the past 24 hours?")
            .setPositiveButton("Regenerate") { _, _ ->
                historyManager.clear()
                isTracking = false // Reset tracking state if needed to avoid conflicts
                loadInitialData() // Will trigger generation since empty
                Toast.makeText(this, "Demo data regenerated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    }

    private fun startTracking() {
        Log.d(TAG, "Starting tracking...")
        
        // Check permissions
        if (!usageStatsHelper.hasPermission()) {
            Toast.makeText(this, "Grant Usage Permission", Toast.LENGTH_LONG).show()
            usageStatsHelper.requestPermission()
            return
        }
        
        // Check call log permission (optional but nice)
        if (!communicationHelper.hasPermissions()) {
             requestPermissions(arrayOf(
                 android.Manifest.permission.READ_CALL_LOG, 
                 android.Manifest.permission.READ_SMS
             ), 101)
        }

        isTracking = true
        btnStartTracking.text = "Stop Tracking"
        btnStartTracking.setBackgroundResource(R.drawable.button_outline)
        btnStartTracking.setTextColor(ContextCompat.getColor(this, R.color.stress_medium))

        featureCollector.resetSession()
        
        // Start service for background collection
        val serviceIntent = Intent(this, DataCollectionService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        // Immediately do first update
        updateStressAnalysis()
        
        // Start periodic updates
        updateHandler.postDelayed(updateRunnable, updateInterval)

        Toast.makeText(this, "Tracking Started", Toast.LENGTH_SHORT).show()
    }

    private fun stopTracking() {
        Log.d(TAG, "Stopping tracking...")
        
        isTracking = false
        btnStartTracking.text = "Start Tracking"
        btnStartTracking.setBackgroundResource(R.drawable.button_primary)
        btnStartTracking.setTextColor(ContextCompat.getColor(this, R.color.white))

        updateHandler.removeCallbacks(updateRunnable)
        stopService(Intent(this, DataCollectionService::class.java))
        
        Toast.makeText(this, "Tracking Stopped", Toast.LENGTH_SHORT).show()
    }

    // Receiver for background updates
    private val stressReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == "com.example.stressdetection.UPDATE_STRESS") {
                val score = intent.getIntExtra("score", 0)
                // Only update if we aren't actively typing (active typing is more accurate)
                if (featureCollector.getTypingSpeed() == 0f) {
                    runOnUiThread {
                        updateStressUI(score)
                    }
                }
            } else if (intent?.action == "com.example.stressdetection.UPDATE_TYPING") {
                val speed = intent.getFloatExtra("speed", 0f)
                val rate = intent.getFloatExtra("errorRate", 0f)
                val clickRate = intent.getFloatExtra("clickRate", 0f)
                val scrollRate = intent.getFloatExtra("scrollRate", 0f)
                
                // Pass global metrics to collector
                featureCollector.setGlobalMetrics(speed, rate, scrollRate)
                
                runOnUiThread {
                    tvTypingSpeed.text = "%.1f".format(speed)
                    tvErrorRate.text = "%.0f%%".format(rate * 100)
                    
                    if (scrollRate > 5.0f) {
                         tvTouchPressure.text = "High Scroll"
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = android.content.IntentFilter()
        filter.addAction("com.example.stressdetection.UPDATE_STRESS")
        filter.addAction("com.example.stressdetection.UPDATE_TYPING")
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stressReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stressReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(stressReceiver)
    }

    private fun syncHistoricalData() {
        Toast.makeText(this, "Analyzing 7-day history...", Toast.LENGTH_SHORT).show()
        Thread {
            val calls = communicationHelper.getCallFrequency(7)
            val sms = communicationHelper.getSmsFrequency(7)
            val appUsage = usageStatsHelper.getCategoryUsage(7)
            
            val callCount = calls.values.sum()
            val socialMins = appUsage["Social"] ?: 0
            
            runOnUiThread {
                tvInsight.text = "History Analysis:\nCalls (7 days): $callCount\nSocial App Usage: ${socialMins/60} hrs\nBased on your history, your social engagement seems ${if(callCount > 5) "High" else "Low"}."
            }
        }.start()
    }

    private fun updateStressAnalysis() {
        // Get features from collector
        val features = featureCollector.getFeatureVector()
        
        // Run inference
        val result = stressInference.analyze(features)
        
        // Logic: active usage (typing) >> background usage >> baseline
        runOnUiThread {
            if (features[0] > 0) { // If typing speed > 0
                updateStressDisplay(result)
            }
            updateMetricsDisplay()
            updateUsageStats()
            updateChart()
        }
        
        // Save to history
        historyManager.saveReading(result, features)
    }
    
    private fun updateStressUI(score: Int) {
        tvStressScore.text = score.toString()
        val level = stressInference.getStressLevel(score / 100f)
        tvStressLabel.text = level.displayName.uppercase()
        tvStressLabel.setTextColor(ContextCompat.getColor(this, level.colorRes))
    }

    private fun updateStressDisplay(result: StressInference.StressResult) {
        updateStressUI(result.score)
        
        // Update trend
        val trend = historyManager.getTrend()
        tvTrendEmoji.text = trend.emoji
        tvTrendText.text = trend.description
    }

    private fun updateMetricsDisplay() {
        val typingSpeed = featureCollector.getTypingSpeed()
        val backspaceRatio = featureCollector.getBackspaceRatio()
        val touchPressure = featureCollector.getAverageTouchPressure()
        
        tvTypingSpeed.text = "%.1f".format(typingSpeed)
        tvErrorRate.text = "%.0f%%".format(backspaceRatio * 100)
        tvTouchPressure.text = "%.2f".format(touchPressure)
    }

    private fun updateUsageStats() {
        if (usageStatsHelper.hasPermission()) {
            try {
                // Show calls/SMS today if permission granted, else app switches
                if (communicationHelper.hasPermissions()) {
                    val calls = communicationHelper.getCallFrequency(1)
                    val callCount = calls.values.sum()
                    tvAppSwitches.text = "$callCount Calls" // Repurposing slot for demo
                } else {
                    tvAppSwitches.text = "${usageStatsHelper.getAppSwitchRate().toInt()}/hr"
                }
                
                tvScreenTime.text = "%.0f min".format(usageStatsHelper.getHourlyScreenTime())
                tvAvgSession.text = "%.1f min".format(usageStatsHelper.getAverageSessionLength() / 60)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting usage stats: ${e.message}")
            }
        }
    }

    private fun setupChart() {
        lineChart.description.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(true)
        lineChart.setPinchZoom(true)
        lineChart.setDrawGridBackground(false)
        lineChart.legend.isEnabled = false

        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = Color.LTGRAY

        val leftAxis = lineChart.axisLeft
        leftAxis.textColor = Color.LTGRAY
        leftAxis.setDrawGridLines(true)
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = 100f
        
        lineChart.axisRight.isEnabled = false
    }

    private fun updateChart() {
        val hourlyData = historyManager.getHourlyAverages()
        val entries = ArrayList<Entry>()

        if (hourlyData.isEmpty()) {
            // Default flat line if no data
             for (i in 0..23) entries.add(Entry(i.toFloat(), 0f))
        } else {
             hourlyData.forEach { (hour, score) ->
                 entries.add(Entry(hour.toFloat(), score.toFloat()))
             }
             // Ensure 0-23 range is covered for visual consistency if needed, 
             // or just plot available points. Let's just plot available for now.
        }
        
        // Include dummy data if still empty (logic safety)
        if (entries.isEmpty()) entries.add(Entry(0f, 0f))

        val dataSet = LineDataSet(entries, "Stress Level")
        dataSet.color = ContextCompat.getColor(this, R.color.stress_medium)
        dataSet.valueTextColor = Color.WHITE
        dataSet.lineWidth = 2f
        dataSet.setCircleColor(ContextCompat.getColor(this, R.color.white))
        dataSet.circleRadius = 3f
        dataSet.setDrawValues(false)
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.setDrawFilled(true)
        dataSet.fillColor = ContextCompat.getColor(this, R.color.stress_medium)
        dataSet.fillAlpha = 50

        val lineData = LineData(dataSet)
        lineChart.data = lineData
        lineChart.invalidate()
    }

    private fun loadInitialData() {
        // Calculate Baseline from History
        Thread {
            // Check if we need demo data
            if (historyManager.getHistory().isEmpty()) {
                Log.d(TAG, "No history found. Generating demo data...")
                historyManager.generateDemoData()
            }
            
            // Get latest data to update UI immediately
            val history = historyManager.getHistory()
            val latest = history.lastOrNull()
            
            val appUsage = usageStatsHelper.getCategoryUsage(7)
            val socialMins = appUsage["Social"] ?: 0
            val dailySocialHours = (socialMins / 60) / 7
            
            // Heuristic: Each hour of daily social usage adds 5 points to baseline
            val baselineScore = (dailySocialHours * 5).toInt().coerceIn(10, 50)
            
            runOnUiThread {
                if (latest != null) {
                    updateStressUI(latest.score)
                } else if (tvStressScore.text == "--" || tvStressScore.text == "0") {
                    updateStressUI(baselineScore)
                }
                
                // Update chart with newly generated data
                updateChart()
                
                // Update trend
                val trend = historyManager.getTrend()
                tvTrendEmoji.text = trend.emoji
                tvTrendText.text = trend.description
                
                // Set initial insight
                val insightText = if (dailySocialHours > 3) 
                    "High social media usage ($dailySocialHours hrs/day) correlates with higher baseline stress."
                else
                    "Your digital balance looks good."
                    
                tvInsight.text = insightText
            }
        }.start()
    }
    
    // Helper to explain metrics
    private fun showMetricInfo(title: String, info: String) {
        AlertDialog.Builder(this, R.style.Theme_StressDetection_Dialog)
            .setTitle(title)
            .setMessage(info)
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun showMoodDialog() {
        val moods = arrayOf(
            "😊 Great (Very Relaxed)",
            "🙂 Good (Relaxed)", 
            "😐 Okay (Neutral)",
            "😟 Stressed (Tense)",
            "😫 Very Stressed (Overwhelmed)"
        )
        
        AlertDialog.Builder(this, R.style.Theme_StressDetection_Dialog)
            .setTitle("How are you feeling right now?")
            .setItems(moods) { _, which ->
                val stressScore = when (which) {
                    0 -> 10
                    1 -> 25
                    2 -> 50
                    3 -> 75
                    else -> 90
                }
                
                // Update display immediately
                tvStressScore.text = stressScore.toString()
                val level = stressInference.getStressLevel(stressScore / 100f)
                tvStressLabel.text = level.displayName.uppercase()
                tvStressLabel.setTextColor(ContextCompat.getColor(this, level.colorRes))
                
                // Save to history
                val result = StressInference.StressResult(
                    probability = stressScore / 100f,
                    score = stressScore,
                    level = level
                )
                historyManager.saveReading(result, floatArrayOf(0f, 0f, 0f, 0f))
                
                Toast.makeText(this, "Mood logged: ${moods[which]}", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Mood logged: score=$stressScore")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Capture key events for typing analysis
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event != null && isTracking) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> featureCollector.onKeyDown(event.keyCode)
                KeyEvent.ACTION_UP -> featureCollector.onKeyUp(event.keyCode)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // Capture touch events
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null && isTracking) {
            featureCollector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        stressInference.close()
        updateHandler.removeCallbacks(updateRunnable)
    }
}
