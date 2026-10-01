# StressSense - Complete Build Specification

## 🎯 PROJECT OVERVIEW

Build an Android application named **StressSense** that measures user stress levels using only the phone's built-in sensors. NO external hardware required. ALL processing must be done ON-DEVICE for privacy.

**Target Users:** Students and Professionals  
**Platform:** Android (min SDK 26, target SDK 34)  
**Language:** Kotlin  
**Architecture:** MVVM + Clean Architecture  
**Output:** Stress Score (0-100) only — no advice, no recommendations

---

## 🛠️ TECH STACK (MANDATORY)

```
- Language: Kotlin 1.9+
- UI: Jetpack Compose
- Architecture: MVVM + Clean Architecture
- DI: Hilt
- Async: Kotlin Coroutines + Flow
- Camera: CameraX
- Face Detection: ML Kit (on-device)
- Audio: AudioRecord API
- ML: TensorFlow Lite (on-device only)
- Database: Room
- Background: WorkManager + Foreground Service
- Charts: Vico (Compose-native charts) or MPAndroidChart
- Navigation: Compose Navigation
- Permissions: Accompanist Permissions
```

---

## 🎨 UI/UX DESIGN SYSTEM

### Design Philosophy
**Minimalist, eye-pleasing, soft gradients.** Inspired by Calm, Headspace, and modern iOS health apps.

### Color Palette
```kotlin
// Primary Gradient (Calming)
GradientStart = Color(0xFFB8C6FF)  // Soft Lavender
GradientMid   = Color(0xFFC4E0E5)  // Soft Teal
GradientEnd   = Color(0xFFD4F1DD)  // Mint Green

// Stress Level Colors
LowStress    = Color(0xFF7FD1AE)  // Soft Green
MidStress    = Color(0xFFFFD89B)  // Soft Amber
HighStress   = Color(0xFFFF9AA2)  // Soft Coral

// Neutrals
Background   = Color(0xFFFAFBFF)  // Off-white
Surface      = Color(0xFFFFFFFF)
TextPrimary  = Color(0xFF2D3142)  // Soft black
TextSecondary= Color(0xFF8E93A8)  // Soft gray
```

### Typography
- Font: **Inter** or **Poppins** (Google Fonts)
- Headings: Light/Regular weights (no bold heaviness)
- Generous spacing, large touch targets

### UI Rules
1. Soft gradients on backgrounds (linear, diagonal)
2. Rounded corners: 20dp-32dp for cards
3. Soft shadows (low elevation, blurred)
4. Smooth animations (300-500ms, ease-in-out)
5. No clutter — lots of whitespace
6. Circular progress indicators for scans
7. Glassmorphism effects optional on cards

---

## 📱 SCREEN-BY-SCREEN SPECIFICATION

### 1. Splash Screen
- Centered logo with soft gradient
- Fade-in animation
- Auto-navigate after 1.5s

### 2. Onboarding (3 screens)
- **Screen 1:** "Measure stress without any wearable"
- **Screen 2:** "100% Private — data never leaves your phone"
- **Screen 3:** "Get instant scores anytime"
- Skip button + swipe indicators
- Permissions requested at the end (Camera, Mic, Activity Recognition)

### 3. Home Screen (Main Dashboard)
Layout (top to bottom):
- Greeting: "Hey there 👋" + current date
- **Big Stress Score Card** (circular gauge, current score)
  - Animated circular ring (low=green, mid=amber, high=coral)
  - Score in center (e.g., "42")
  - Label below: "Moderate Stress"
- **"Quick Scan" Button** (primary CTA, gradient background)
- **Today's Trend** mini chart (line graph, hourly)
- Bottom Nav: Home | History | Settings

### 4. Quick Scan Screen
Sequential flow:
- **Step 1: Finger PPG** (20 sec)
  - Instruction: "Cover the rear camera with your fingertip"
  - Circular animated progress
  - Live HR display once detected
- **Step 2: Voice Sample** (10 sec)
  - "Say: The quick brown fox jumps over the lazy dog"
  - Animated waveform visualization
- **Step 3: Facial Scan** (10 sec, optional)
  - Front camera with face oval overlay
  - "Look at the camera naturally"
- **Result Screen:**
  - Big animated number reveal
  - Soft confetti or pulse animation
  - "Done" button → back to Home

### 5. History Screen
- Toggle: Day | Week | Month
- Line chart with stress trend
- List of past scans with timestamps and scores
- Tap entry to see breakdown (HRV, Voice, etc.)

### 6. Settings Screen
- Toggle: Passive Monitoring (on/off)
- Toggle: Notifications
- Calibration: "Recalibrate baseline"
- About: Version, Privacy Policy
- Permissions management

---

## 🧠 CORE ALGORITHM: PPG / HRV DETECTION

This is the **most important feature**. Implement carefully.

### PPG Signal Acquisition

```
1. Open rear camera using CameraX
2. Turn on flash (torch mode = ON)
3. Capture frames at 30 FPS for 20 seconds (600 frames)
4. For each frame:
   - Convert to RGB
   - Extract MEAN of RED channel from center 50% of frame
   - Store as time-series data point with timestamp
5. Verify finger contact:
   - Red mean must be > 200 (high red = finger covering)
   - Green/Blue means must be < 50
   - If not detected, show error: "Please cover camera properly"
```

### Signal Processing Pipeline

```
RAW RED SIGNAL
    ↓
1. Detrending (remove DC component using moving average subtraction)
    ↓
2. Bandpass Filter (Butterworth, 0.7Hz - 4Hz = 42-240 BPM range)
    ↓
3. Normalization (min-max to 0-1)
    ↓
4. Peak Detection (find local maxima with min distance = 0.4s)
    ↓
5. Calculate R-R Intervals (time between consecutive peaks in ms)
    ↓
6. Outlier Rejection (remove intervals beyond ±20% of median)
    ↓
7. Compute HR & HRV metrics
```

### HRV Metrics to Compute

```kotlin
// Time-domain HRV metrics
HR        = 60000 / mean(RR_intervals)  // Heart Rate in BPM
SDNN      = standardDeviation(RR_intervals)  // Overall HRV
RMSSD     = sqrt(mean(squaredDifferences(RR_intervals)))  // Short-term HRV
pNN50     = (count of |RR_diff| > 50ms) / total_RRs * 100

// Stress indicator (key metric)
// LOW RMSSD = HIGH STRESS
// HIGH RMSSD = LOW STRESS (good parasympathetic activity)
```

### Stress Score Mapping (HRV-based)

```kotlin
fun calculateHRVStressScore(rmssd: Double): Int {
    // Typical RMSSD: 20-100ms
    // <20ms = very high stress
    // >70ms = low stress
    return when {
        rmssd >= 70 -> (10..25).random()      // Very low stress
        rmssd >= 50 -> (26..40).random()      // Low stress
        rmssd >= 35 -> (41..55).random()      // Moderate
        rmssd >= 20 -> (56..75).random()      // High
        else        -> (76..95).random()      // Very high
    }
}
```

### Voice Analysis Algorithm

```
1. Record 10-second audio at 16kHz, mono
2. Extract features using TarsosDSP or custom:
   - Pitch (F0) using YIN algorithm
   - Jitter (cycle-to-cycle pitch variation)
   - Shimmer (amplitude variation)
   - Speech rate (syllables/sec)
3. High jitter + high pitch = stress indicator
4. Output: Voice Stress Score (0-100)
```

### Facial Analysis Algorithm

```
1. Use ML Kit Face Detection
2. Track for 10 seconds:
   - Blink rate (count blinks / 10s) - normal: 1.5-2/sec
   - Eye openness ratio
   - Smile probability (low = stress)
3. Output: Facial Stress Score (0-100)
```

### Final Composite Score

```kotlin
fun computeFinalStressScore(
    hrvScore: Int,      // weight 0.5
    voiceScore: Int?,   // weight 0.25
    facialScore: Int?,  // weight 0.15
    typingScore: Int?,  // weight 0.05 (passive)
    usageScore: Int?    // weight 0.05 (passive)
): Int {
    var total = hrvScore * 0.5
    var weights = 0.5
    
    voiceScore?.let { total += it * 0.25; weights += 0.25 }
    facialScore?.let { total += it * 0.15; weights += 0.15 }
    typingScore?.let { total += it * 0.05; weights += 0.05 }
    usageScore?.let { total += it * 0.05; weights += 0.05 }
    
    return (total / weights).toInt().coerceIn(0, 100)
}
```

---

## 🌙 PASSIVE MONITORING IMPLEMENTATION

### Typing Pattern Analysis
- Use **AccessibilityService** to capture keystroke events
- Track per session:
  - Typing speed (chars/min)
  - Backspace ratio (high = stress)
  - Inter-key latency variance (high = stress)
- DO NOT log actual text content (privacy!)
- Store only metrics

### Phone Usage Patterns
- Use **UsageStatsManager**
- Track:
  - Screen unlocks per hour
  - App switches per hour
  - Late-night usage (11pm-3am)
- High frequency = potential stress signal

### Motion Patterns
- Use **SensorManager** (accelerometer)
- Track micro-tremors during phone holding
- Sample at 50Hz during active phone use
- High-frequency jitter = possible stress

### Daily Aggregation
- WorkManager runs every 6 hours
- Aggregates passive signals into a daily passive score
- Combined with last active scan for overall daily score

---

## 💾 DATA MODEL (Room Database)

```kotlin
@Entity(tableName = "stress_scans")
data class StressScan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val finalScore: Int,
    val hrvScore: Int?,
    val voiceScore: Int?,
    val facialScore: Int?,
    val heartRate: Int?,
    val rmssd: Double?,
    val sdnn: Double?,
    val scanType: String  // "ACTIVE" or "PASSIVE"
)

@Entity(tableName = "passive_metrics")
data class PassiveMetric(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val typingScore: Int?,
    val usageScore: Int?,
    val motionScore: Int?
)

@Entity(tableName = "user_baseline")
data class UserBaseline(
    @PrimaryKey val id: Int = 1,
    val baselineRMSSD: Double,
    val baselineHR: Double,
    val calibrationDate: Long,
    val sampleCount: Int
)
```

---

## 📂 PROJECT STRUCTURE

```
app/
├── src/main/java/com/stresssense/app/
│   ├── data/
│   │   ├── local/        (Room DAOs, Database)
│   │   ├── repository/   (Repository implementations)
│   │   └── models/       (Data classes)
│   ├── domain/
│   │   ├── usecases/     (ComputeStressScore, GetHistoryUseCase, etc.)
│   │   └── algorithms/   (PPGProcessor, HRVCalculator, VoiceAnalyzer, FaceAnalyzer)
│   ├── presentation/
│   │   ├── ui/
│   │   │   ├── home/
│   │   │   ├── scan/
│   │   │   ├── history/
│   │   │   ├── settings/
│   │   │   └── onboarding/
│   │   ├── theme/        (Colors, Typography, Theme.kt)
│   │   └── components/   (Reusable Composables)
│   ├── services/
│   │   ├── PassiveMonitoringService.kt
│   │   ├── TypingAccessibilityService.kt
│   │   └── workers/      (DailyAggregationWorker)
│   ├── di/               (Hilt modules)
│   └── MainActivity.kt
├── res/
└── AndroidManifest.xml
```

---

## 🔐 PERMISSIONS REQUIRED

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions" />
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FLASHLIGHT" />

<uses-feature android:name="android.hardware.camera" />
<uses-feature android:name="android.hardware.camera.flash" />
```

---

## ✅ BUILD CHECKLIST (Execute in Order)

### Phase 1: Foundation
- [ ] Initialize Android project (Kotlin + Compose)
- [ ] Set up Hilt, Room, Navigation, CameraX dependencies
- [ ] Create theme system (colors, typography, gradient backgrounds)
- [ ] Build Splash + Onboarding screens
- [ ] Implement permissions flow

### Phase 2: Core Algorithm
- [ ] Implement PPGProcessor class (camera frame capture + red channel extraction)
- [ ] Implement signal processing pipeline (detrend, filter, peak detection)
- [ ] Implement HRVCalculator (RMSSD, SDNN, HR)
- [ ] Build stress score mapping function
- [ ] Unit test the algorithm with sample data

### Phase 3: Active Scan UI
- [ ] Build Quick Scan screen with CameraX integration
- [ ] Add real-time HR display
- [ ] Add scan progress animation
- [ ] Build result reveal screen with animations

### Phase 4: Home & History
- [ ] Build Home dashboard with circular gauge
- [ ] Implement Room database + Repository
- [ ] Build History screen with charts
- [ ] Add trend visualizations

### Phase 5: Voice & Face
- [ ] Add voice recording + analysis
- [ ] Add face detection + blink rate tracking
- [ ] Integrate into composite score

### Phase 6: Passive Monitoring
- [ ] Create AccessibilityService for typing patterns
- [ ] Create UsageStatsManager integration
- [ ] Build foreground service
- [ ] Create WorkManager daily aggregation

### Phase 7: Polish
- [ ] Settings screen
- [ ] Baseline calibration flow
- [ ] Smooth animations everywhere
- [ ] Empty states and error handling
- [ ] Performance optimization

---

## 🎯 CRITICAL CONSTRAINTS

1. **NEVER** send any data to any server — everything on-device
2. **NEVER** log actual typed text — only metrics
3. **NO** monetization, ads, or analytics SDKs
4. Battery usage must be minimal in passive mode
5. UI must feel **calm and premium** — no harsh colors
6. All operations must be **non-blocking** (use Coroutines)
7. App should work offline 100%

---

## 📦 KEY DEPENDENCIES (build.gradle)

```kotlin
dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    
    // ML Kit
    implementation("com.google.mlkit:face-detection:16.1.6")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Charts
    implementation("com.patrykandpatrick.vico:compose:1.13.1")
    implementation("com.patrykandpatrick.vico:compose-m3:1.13.1")
    
    // Audio
    implementation("be.tarsos.dsp:core:2.5")
    
    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

---

## 📊 ON-DEVICE ML PERFORMANCE SPECIFICATIONS

| Metric | Target / Benchmark | Implementation Detail |
|--------|--------------------|-----------------------|
| **Accuracy** | **91.2% (INT8 TFLite)** / **93.5% (GB Teacher)** | 3-class classification accuracy (Low/Medium/High) evaluated on stratified test dataset using knowledge distillation |
| **Model Size** | **5.77 KB** | Ultra-compact `stress_model.tflite` asset produced via INT8 full-integer quantization for zero storage overhead |
| **Inference Latency** | **< 1.8 ms** | Sub-2 millisecond CPU execution per inference batch for real-time background evaluation |
| **Signal Processing Latency** | **< 50 ms total** | PPG processing: ~12ms/frame batch; Voice YIN analysis: ~45ms/10s buffer; Facial ML Kit: ~18ms/frame |

---

## 🚀 FINAL INSTRUCTION TO AGENT

Build this Android app called **StressSense** following the specification above EXACTLY. 
- Start with Phase 1 and proceed sequentially.
- After each phase, ensure the app compiles and runs.
- Use clean, well-commented Kotlin code.
- Prioritize the PPG/HRV algorithm accuracy.
- Make the UI feel premium with soft gradients and smooth animations.
- ALL processing must be on-device.
- Generate the complete codebase.