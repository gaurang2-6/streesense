# Stress Detection - Android Project

This folder contains the source code for the StressDetect Android application.

## Structure

```
android_app/
├── build.gradle                   # Project-level Gradle config (plugins)
├── settings.gradle                # Gradle settings (repositories, modules)
│
└── app/
    ├── build.gradle               # App-level dependencies
    ├── proguard-rules.pro         # ProGuard configuration
    │
    └── src/main/
        ├── AndroidManifest.xml    # App manifest & permissions
        ├── assets/
        │   └── stress_model.tflite  # TensorFlow Lite model for inference
        │
        ├── java/com/example/stressdetection/
        │   ├── OnboardingActivity.kt     # 3-page user onboarding flow
        │   ├── MainActivity.kt         # Main dashboard & stress visualization
        │   ├── FeatureCollector.kt     # Typing/touch feature extraction
        │   ├── StressInference.kt      # TFLite model wrapper & inference
        │   ├── UsageStatsHelper.kt     # App usage pattern analysis
        │   ├── StressHistoryManager.kt # Historical data & trends
        │   ├── DataCollectionService.kt  # Foreground service for background collection
        │   ├── GlobalInputService.kt   # Accessibility service for global typing
        │   ├── BootReceiver.kt         # Auto-start on boot capability
        │   ├── SurveyActivity.kt       # Detailed stress survey screen
        │   └── CommunicationHelper.kt    # Call/SMS frequency analysis
        │
        └── res/
            ├── layout/
            │   ├── activity_main.xml       # Main dashboard layout
            │   ├── activity_onboarding.xml # Onboarding screens
            │   ├── item_onboarding.xml     # Onboarding page item
            │   ├── activity_survey.xml     # Survey screen layout
            │   └── item_survey_question.xml  # Survey question item
            │
            ├── drawable/
            │   ├── button_primary.xml      # Gradient button background
            │   ├── button_outline.xml      # Outline button background
            │   ├── stress_ring_background.xml
            │   ├── trend_chip_background.xml
            │   └── ic_settings.xml
            │
            ├── values/
            │   ├── colors.xml         # Dark theme color palette
            │   ├── strings.xml        # App strings
            │   └── themes.xml         # Material dark theme
            │
            ├── mipmap-anydpi-v26/    # Launcher icons
            └── xml/
                ├── backup_rules.xml
                └── data_extraction_rules.xml
```

## Key Components

### `MainActivity.kt`
- Dashboard entry point with stress score display (0-100)
- Captures typing events via `dispatchKeyEvent`
- Captures touch events via `dispatchTouchEvent`
- Starts/stops `DataCollectionService`
- Displays hourly stress chart using MPAndroidChart

### `OnboardingActivity.kt`
- 3-page introduction explaining the app's purpose
- Privacy-first messaging
- Sets `onboarded` flag in SharedPreferences

### `FeatureCollector.kt`
- Collects typing metrics: speed, backspace ratio, key hold time
- Collects touch metrics: pressure, swipe velocity, duration
- Normalizes features to model input format
- 1-minute sliding window for data aggregation

### `StressInference.kt`
- Loads TensorFlow Lite model from assets
- Runs inference with 4-feature input vector
- Provides fallback heuristic if model fails
- Maps probability to stress levels (Low/Medium/High)

### `StressHistoryManager.kt`
- Persists stress readings to SharedPreferences
- Generates demo data when no history exists
- Calculates hourly averages for charting
- Determines trend direction (improving/stable/worsening)

### `DataCollectionService.kt`
- Foreground service for background monitoring
- Uses accelerometer for agitation detection
- Broadcasts stress updates to MainActivity
- Calculates background stress from movement + usage

### `GlobalInputService.kt`
- Accessibility service for cross-app typing detection
- Captures text changes system-wide
- Broadcasts metrics to MainActivity

## Permissions

| Permission | Purpose |
|------------|---------|
| `PACKAGE_USAGE_STATS` | App usage pattern analysis |
| `FOREGROUND_SERVICE` | Background monitoring |
| `POST_NOTIFICATIONS` | Status updates |
| Accessibility Service | Global typing detection |

## Building

```bash
# From project root
./gradlew build

# Install on connected device
./gradlew installDebug
```

Requires Android SDK 24+ and JDK 17+.