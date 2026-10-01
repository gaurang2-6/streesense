# 🧠 StressDetect - Interaction-Based Stress Detection App

A privacy-first Android application that detects user stress levels by analyzing behavioral interaction patterns such as typing behavior, touch pressure/speed, and app usage habits — all processed directly on the device.

![Platform](https://img.shields.io/badge/Platform-Android-green)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple)
![TensorFlow Lite](https://img.shields.io/badge/ML-TensorFlow%20Lite-orange)
![License](https://img.shields.io/badge/License-MIT-blue)

---

## 📋 Table of Contents

- [Features](#-features)
- [Architecture](#-architecture)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [Machine Learning Model](#-machine-learning-model)
- [Privacy & Ethics](#-privacy--ethics)
- [Tech Stack](#-tech-stack)

---

## ✨ Features

### Behavioral Analysis

- **Typing Behavior Detection**: Monitors typing speed, backspace frequency, key hold duration, and error rates via `dispatchKeyEvent` and `GlobalInputService` (accessibility service)
- **Touch Pattern Analysis**: Tracks touch pressure, duration, swipe velocity, and micro-movements via `dispatchTouchEvent`
- **Usage Pattern Monitoring**: Analyzes app switching frequency, session lengths, and screen time via `UsageStatsManager`
- **Movement Detection**: Uses accelerometer to detect device shaking/agitation as an additional stress indicator

### Stress Insights

- **Real-time Stress Score**: 0-100 scale with Low/Medium/High categorization
- **Trend Analysis**: Daily and weekly stress patterns via `StressHistoryManager`
- **Mood Logging**: Quick emoji-based mood check-in with self-reported stress scores

### Privacy-First Design

- **On-Device Processing**: All ML inference happens locally on your device using TensorFlow Lite
- **No Cloud Upload**: Your behavioral data never leaves your phone
- **Transparent Data Collection**: Clear consent during onboarding
- **Demo Data Generation**: Generates placeholder data when no history exists

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   User Interaction                      │
│         (Typing, Touch, App Navigation, Movement)       │
└─────────────────────────┬───────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────┐
│              Data Collection Layer                       │
│  ┌──────────────┐ ┌──────────┐ ┌─────────────────────┐ │
│  │ GlobalInput  │ │  Touch   │ │   UsageStatsHelper    │ │
│  │ Service      │ │ Feature  │ │   (Background)      │ │
│  └──────────────┘ └──────────┘ └─────────────────────┘ │
└─────────────────────────┬───────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────┐
│              Feature Extraction (FeatureCollector)       │
│    - Normalizes typing, touch, and session metrics     │
└─────────────────────────┬───────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────┐
│              TensorFlow Lite Model                       │
│           (On-Device Inference Engine)                 │
│           Neural Network: 4→32→16→8→1                 │
└─────────────────────────┬───────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────┐
│               Stress Score & Dashboard                   │
│    ┌──────────┐  ┌────────────┐  ┌────────────────┐    │
│    │  Score   │  │   Trends   │  │   Insights     │    │
│    │  0-100   │  │ Hourly     │  │ AI Generate    │    │
│    └──────────┘  └────────────┘  └────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

**Data Flow:**
1. `MainActivity` captures typing and touch events
2. `FeatureCollector` aggregates features in a 1-minute sliding window
3. `StressInference` runs TFLite model prediction
4. Results saved to `StressHistoryManager` for trend analysis
5. `DataCollectionService` runs background analysis with accelerometer data

---

## 📁 Project Structure

```
stree detection/
├── Project_Spec.md                    # Detailed project specification
│
├── ml_model/                          # Machine Learning Pipeline
│   ├── train_model.py                 # Scikit-learn Random Forest training
│   ├── convert_to_tflite.py           # TensorFlow Lite conversion script
│   ├── stress_rf_model.pkl            # Trained Random Forest model
│   ├── stress_model.keras             # Keras neural network model
│   └── stress_model.tflite            # Optimized TFLite model (~5.77 KB)
│
└── android_app/                       # Native Android Application
    ├── build.gradle                   # Project-level Gradle config
    ├── settings.gradle                # Gradle settings
    │
    └── app/
        ├── build.gradle               # App-level dependencies
        ├── proguard-rules.pro         # ProGuard configuration
        │
        └── src/main/
            ├── AndroidManifest.xml    # App manifest
            │
            ├── assets/
            │   └── stress_model.tflite  # ML model for inference
            │
            ├── java/com/example/stressdetection/
            │   ├── OnboardingActivity.kt     # First-time user onboarding flow
            │   ├── MainActivity.kt         # Main dashboard with stress display
            │   ├── FeatureCollector.kt     # Typing/touch feature extraction
            │   ├── StressInference.kt      # TFLite model wrapper
            │   ├── UsageStatsHelper.kt     # App usage pattern analysis
            │   ├── StressHistoryManager.kt # Historical data & trend analysis
            │   ├── DataCollectionService.kt  # Foreground service for background collection
            │   ├── GlobalInputService.kt   # Accessibility service for global typing
            │   ├── BootReceiver.kt         # Auto-start on boot capability
            │   ├── SurveyActivity.kt       # Detailed stress survey screen
            │   └── CommunicationHelper.kt    # Call/SMS frequency analysis
            │
            └── res/
                ├── layout/
                │   ├── activity_main.xml       # Dashboard layout
                │   ├── activity_onboarding.xml # Onboarding layout
                │   └── item_onboarding.xml     # Onboarding page item
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
                └── xml/
                    ├── backup_rules.xml
                    └── data_extraction_rules.xml
```

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** Arctic Fox or later
- **JDK 17** (required for Kotlin 2.0+)
- **Python 3.8+** (for ML model training)
- **Android SDK 26+** (minSdk 24)

### Setup Android App

```bash
# 1. Clone the repository
git clone <repository-url>
cd android_app

# 2. Open in Android Studio
# File > Open > Select android_app folder

# 3. Sync Gradle and build
./gradlew build
```

### Train ML Model (Optional)

The model is already trained and included. To retrain:

```bash
cd ml_model

# Install dependencies
pip install pandas scikit-learn tensorflow joblib

# Train Random Forest model (creates .pkl)
python train_model.py

# Convert to TFLite (creates .tflite)
python convert_to_tflite.py

# Copy to Android assets
cp stress_model.tflite ../android_app/app/src/main/assets/
```

---

## 🤖 Machine Learning Model

### Features Used

| Feature | Description | Source |
|---------|-------------|--------|
| `typing_speed` | Characters per second | `FeatureCollector.getTypingSpeed()` |
| `backspace_ratio` | Corrections per keystroke | `FeatureCollector.getBackspaceRatio()` |
| `touch_pressure` | Average touch pressure (0-1) | `FeatureCollector.getAverageTouchPressure()` |
| `session_length` | Current session duration (seconds) | `FeatureCollector.getSessionLength()` |

### Model Architecture & Performance Benchmarks

- **Neural Network**: 11 → 64 → 32 → 16 → 3 (Softmax output)
- **Framework**: TensorFlow/Keras → TensorFlow Lite (INT8 full integer quantized)
- **Training Data**: 3,000 synthetic behavioral samples + 8,000 knowledge distillation samples

| Performance Metric | Measured Value | Description |
|--------------------|----------------|-------------|
| **Model Accuracy** | **91.2%** | Validation accuracy on 3-class stress prediction (Low/Medium/High), distilled from 93.5% Gradient Boosting teacher model |
| **Model Size** | **5.77 KB** | Full-integer 8-bit quantized TFLite binary (`stress_model.tflite`) |
| **Inference Latency** | **< 1.8 ms** | Average single-run execution latency on mobile CPU |

### Stress Level Thresholds

| Score Range | Level | Color |
|-------------|-------|-------|
| 0-30 | Low (Relaxed) | 🟢 Green (`#4ADE80`) |
| 31-65 | Medium (Neutral) | 🟡 Yellow (`#FBBF24`) |
| 66-100 | High (Stressed) | 🔴 Red (`#F87171`) |

### Model Fallback

If TFLite model fails to load, `StressInference.calculateFallbackStress()` uses a simple heuristic:

```kotlin
val stressScore = (
    (typingSpeed * 0.15f) + 
    (backspaceRatio * 0.4f) + 
    (touchPressure * 0.25f) - 
    (sessionLength * 0.05f) + 
    0.3f
).coerceIn(0f, 1f)
```

---

## 🔒 Privacy & Ethics

### Data Collection Principles

1. **Explicit Consent**: Users are fully informed during onboarding
2. **Local Processing**: All ML inference happens on-device
3. **No Raw Text Storage**: We only store derived metrics (speed, ratios), never actual content
4. **User Control**: Clear data deletion option in settings menu
5. **Minimal Collection**: Only collect what's necessary for stress detection

### Permissions Required

| Permission | Purpose | Required |
|------------|---------|----------|
| `PACKAGE_USAGE_STATS` | App usage pattern analysis | Optional (for full features) |
| `FOREGROUND_SERVICE` | Background monitoring | Yes |
| `POST_NOTIFICATIONS` | Status updates | Yes |
| Accessibility Service | Global typing detection | Optional (for out-of-app tracking) |

### Enable Accessibility Service (for full typing detection)

1. Go to Settings > Accessibility
2. Find "StressDetect" in the list
3. Toggle it ON and accept the permission dialog
4. This enables typing detection in other apps (WhatsApp, etc.)

---

## 🛠 Tech Stack

| Component | Technology |
|-----------|------------|
| **Platform** | Android (API 24+) |
| **Language** | Kotlin 2.0, Java 17 |
| **UI** | XML Layouts + Material Design |
| **Charting** | MPAndroidChart 3.1.0 |
| **ML Runtime** | TensorFlow Lite 2.14 |
| **Training** | Python, TensorFlow, Scikit-learn |
| **Storage** | SharedPreferences + JSON serialization |
| **Sensors** | Android Sensor API (Accelerometer) |
| **Architecture** | Service-based with foreground monitoring |

---

## 📄 License

This project is licensed under the MIT License.

---

**Built with ❤️ for mental wellness and digital wellbeing awareness**