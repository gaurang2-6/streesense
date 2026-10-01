# StressSense — Android App Implementation Plan

Build a complete Android application that measures user stress levels using only built-in phone sensors (camera PPG, microphone, front camera). All processing on-device, MVVM + Clean Architecture, Jetpack Compose UI.

## User Review Required

> [!IMPORTANT]
> This is a **very large project** (~60+ Kotlin files, complex algorithms, hardware integrations). Building the complete codebase will take significant time. The plan below covers all 7 phases from the spec.

> [!WARNING]
> **TarsosDSP** (`be.tarsos.dsp:core:2.5`) is not available on Maven Central for Android. I will implement the voice analysis (pitch/jitter/shimmer extraction) using custom Kotlin code with `AudioRecord` API directly, which is more reliable and avoids dependency issues.

> [!WARNING]
> **Vico charts** library API has changed significantly across versions. I'll use `com.patrykandpatrick.vico:compose-m3:1.13.1` as specified, but may need to adjust the API usage to match the actual available version.

## Open Questions

> [!IMPORTANT]
> **Application ID**: I'll use `com.stresssense.app` as the package name. Let me know if you prefer something different.

> [!IMPORTANT]
> **Gradle version**: I'll use Gradle 8.2+ with AGP 8.2+ and Kotlin 1.9.22. The project will use the Kotlin DSL (`build.gradle.kts`).

> [!NOTE]
> **TensorFlow Lite**: The spec mentions TF Lite but doesn't specify any custom models. I'll structure the code to allow TF Lite model integration later, but the initial build will use the algorithmic approaches (HRV calculation, voice feature extraction) described in the spec, which don't require ML models.

---

## Proposed Changes

The entire project will be created from scratch in `d:\stresssence\`. Below is the component-by-component breakdown.

---

### Phase 1: Foundation — Project Setup & Theme

#### [NEW] Project Configuration Files

| File | Purpose |
|------|---------|
| `build.gradle.kts` (root) | Root build script with plugin declarations |
| `settings.gradle.kts` | Project settings, repository config |
| `gradle.properties` | Gradle/Android properties |
| `app/build.gradle.kts` | App module with all dependencies |
| `gradle/libs.versions.toml` | Version catalog for dependencies |

#### [NEW] Theme System — `app/src/main/java/com/stresssense/app/presentation/theme/`

| File | Purpose |
|------|---------|
| `Color.kt` | Full color palette (gradients, stress levels, neutrals) |
| `Type.kt` | Typography with Inter/Poppins font family |
| `Theme.kt` | Material3 theme with custom color scheme |
| `Shape.kt` | Rounded corner shapes (20dp-32dp) |
| `Gradient.kt` | Reusable gradient brush definitions |

#### [NEW] `app/src/main/java/com/stresssense/app/presentation/ui/splash/SplashScreen.kt`
- Centered logo, soft gradient background, fade-in animation, auto-navigate after 1.5s

#### [NEW] `app/src/main/java/com/stresssense/app/presentation/ui/onboarding/OnboardingScreen.kt`
- 3-page horizontal pager with swipe indicators, skip button, permission request at end

#### [NEW] `app/src/main/AndroidManifest.xml`
- All required permissions, service declarations, activity config

#### [NEW] `app/src/main/java/com/stresssense/app/MainActivity.kt`
- Single activity, Compose content host, Hilt entry point

#### [NEW] `app/src/main/java/com/stresssense/app/StressSenseApp.kt`
- Application class with `@HiltAndroidApp`

#### [NEW] Navigation — `app/src/main/java/com/stresssense/app/presentation/navigation/`
- `NavGraph.kt` — Compose Navigation graph with all routes
- `Screen.kt` — Sealed class defining screen routes

---

### Phase 2: Core Algorithm — PPG/HRV Processing

#### [NEW] `app/src/main/java/com/stresssense/app/domain/algorithms/PPGProcessor.kt`
- CameraX frame analysis, red channel extraction from center 50% of frame
- Finger contact verification (red mean > 200, green/blue < 50)
- Time-series signal collection at 30 FPS for 20 seconds

#### [NEW] `app/src/main/java/com/stresssense/app/domain/algorithms/SignalProcessor.kt`
- Detrending (moving average subtraction)
- Butterworth bandpass filter (0.7Hz–4Hz)
- Min-max normalization
- Peak detection with minimum distance = 0.4s

#### [NEW] `app/src/main/java/com/stresssense/app/domain/algorithms/HRVCalculator.kt`
- R-R interval calculation from peaks
- Outlier rejection (±20% of median)
- HR, SDNN, RMSSD, pNN50 computation
- Stress score mapping (RMSSD → 0-100 score)

#### [NEW] `app/src/main/java/com/stresssense/app/domain/algorithms/StressScoreCalculator.kt`
- Composite score calculation with weighted inputs
- HRV (0.5), Voice (0.25), Facial (0.15), Typing (0.05), Usage (0.05)

---

### Phase 3: Active Scan UI

#### [NEW] `app/src/main/java/com/stresssense/app/presentation/ui/scan/ScanScreen.kt`
- Multi-step sequential scan flow (PPG → Voice → Face → Result)
- Each step with animated progress, instructions, live feedback

#### [NEW] `app/src/main/java/com/stresssense/app/presentation/ui/scan/ScanViewModel.kt`
- State management for scan flow, CameraX lifecycle, result computation

#### [NEW] `app/src/main/java/com/stresssense/app/presentation/ui/scan/components/`
- `PPGScanView.kt` — Finger PPG scan UI with circular progress, live HR
- `VoiceScanView.kt` — Voice recording UI with waveform visualization
- `FacialScanView.kt` — Front camera with face oval overlay
- `ScanResultView.kt` — Animated number reveal with pulse animation

#### [NEW] `app/src/main/java/com/stresssense/app/presentation/components/`
- `CircularProgressIndicator.kt` — Custom animated circular gauge
- `AnimatedWaveform.kt` — Audio waveform visualization
- `GradientButton.kt` — Primary CTA with gradient background
- `StressGauge.kt` — Circular stress score display with color-coded ring

---

### Phase 4: Home & History

#### [NEW] `app/src/main/java/com/stresssense/app/presentation/ui/home/HomeScreen.kt`
- Greeting, big stress score card, Quick Scan button, today's trend chart

#### [NEW] `app/src/main/java/com/stresssense/app/presentation/ui/home/HomeViewModel.kt`
- Latest scan data, daily trend aggregation

#### [NEW] `app/src/main/java/com/stresssense/app/presentation/ui/history/HistoryScreen.kt`
- Day/Week/Month toggle, line chart, scan list with tap-to-expand

#### [NEW] `app/src/main/java/com/stresssense/app/presentation/ui/history/HistoryViewModel.kt`

#### [NEW] Room Database — `app/src/main/java/com/stresssense/app/data/local/`
- `StressSenseDatabase.kt` — Room database with all entities
- `StressScanDao.kt` — DAO for stress scans
- `PassiveMetricDao.kt` — DAO for passive metrics
- `UserBaselineDao.kt` — DAO for user baseline

#### [NEW] Data Models — `app/src/main/java/com/stresssense/app/data/models/`
- `StressScan.kt`, `PassiveMetric.kt`, `UserBaseline.kt` — Room entities

#### [NEW] Repository — `app/src/main/java/com/stresssense/app/data/repository/`
- `StressRepository.kt` — Interface
- `StressRepositoryImpl.kt` — Implementation

#### [NEW] Use Cases — `app/src/main/java/com/stresssense/app/domain/usecases/`
- `SaveScanUseCase.kt`, `GetHistoryUseCase.kt`, `GetLatestScanUseCase.kt`

---

### Phase 5: Voice & Face Analysis

#### [NEW] `app/src/main/java/com/stresssense/app/domain/algorithms/VoiceAnalyzer.kt`
- AudioRecord at 16kHz mono, 10-second capture
- Custom pitch detection (YIN algorithm implementation)
- Jitter, shimmer, speech rate calculation
- Voice stress score output

#### [NEW] `app/src/main/java/com/stresssense/app/domain/algorithms/FaceAnalyzer.kt`
- ML Kit Face Detection integration
- Blink rate tracking over 10 seconds
- Eye openness ratio, smile probability
- Facial stress score output

---

### Phase 6: Passive Monitoring

#### [NEW] `app/src/main/java/com/stresssense/app/services/PassiveMonitoringService.kt`
- Foreground service for continuous passive monitoring
- Accelerometer micro-tremor detection

#### [NEW] `app/src/main/java/com/stresssense/app/services/TypingAccessibilityService.kt`
- AccessibilityService for typing pattern analysis
- Typing speed, backspace ratio, inter-key latency variance
- **No text content logging**

#### [NEW] `app/src/main/java/com/stresssense/app/services/workers/DailyAggregationWorker.kt`
- WorkManager periodic task (every 6 hours)
- Aggregates passive signals into daily passive score

---

### Phase 7: Settings & Polish

#### [NEW] `app/src/main/java/com/stresssense/app/presentation/ui/settings/SettingsScreen.kt`
- Passive monitoring toggle, notifications toggle
- Baseline recalibration, about info, permissions management

#### [NEW] `app/src/main/java/com/stresssense/app/presentation/ui/settings/SettingsViewModel.kt`

#### [NEW] `app/src/main/java/com/stresssense/app/di/`
- `AppModule.kt` — Hilt module for database, repository, algorithm providers
- `ServiceModule.kt` — Hilt module for services

---

## Verification Plan

### Automated Tests
- The project will compile with `./gradlew assembleDebug`
- Algorithm unit tests for HRV calculation with sample R-R interval data

### Manual Verification
- App launches and shows splash → onboarding flow
- Quick scan flow operates correctly with camera/mic
- History screen displays stored scans
- Settings toggles work

---

## File Count Summary

| Category | Files |
|----------|-------|
| Gradle/Config | 5 |
| Theme | 6 |
| Screens & ViewModels | 14 |
| Components | 6 |
| Algorithms | 5 |
| Data Layer | 9 |
| Services | 3 |
| DI | 2 |
| Navigation | 2 |
| Core (App, Activity, Manifest) | 3 |
| **Total** | **~55 files** |
