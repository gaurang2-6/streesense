# StressDetect - Interaction-Based Stress Detection App

A privacy-first Android application that detects user stress levels by analyzing behavioral interaction patterns such as typing behavior, touch pressure/speed, and app usage habits.

![Platform](https://img.shields.io/badge/Platform-Android-green)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple)
![TensorFlow](https://img.shields.io/badge/ML-TensorFlow%20Lite-orange)
![License](https://img.shields.io/badge/License-MIT-blue)

---

## Table of Contents

- [Features](#-features)
- [Architecture](#-architecture)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [Machine Learning Model](#-machine-learning-model)
- [Privacy & Ethics](#-privacy--ethics)
- [Screenshots](#-screenshots)
- [Tech Stack](#-tech-stack)

---

##  Features

### Behavioral Analysis
- **Typing Behavior Detection**: Monitors typing speed, backspace frequency, key hold duration, and error rates
- **Touch Pattern Analysis**: Tracks touch pressure, duration, swipe velocity, and micro-movements
- **Usage Pattern Monitoring**: Analyzes app switching frequency, session lengths, and screen time

### Stress Insights
- **Real-time Stress Score**: 0-100 scale with Low/Medium/High categorization
- **Trend Analysis**: Daily and weekly stress patterns
- **Personalized Insights**: AI-generated recommendations based on your patterns

### Privacy-First Design
- **On-Device Processing**: All ML inference happens locally on your device
- **No Cloud Upload**: Your behavioral data never leaves your phone
- **Transparent Data Collection**: Clear consent during onboarding

---

##  Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    User Interaction                      │
│         (Typing, Touch, App Navigation)                  │
└─────────────────────────┬───────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────┐
│              Feature Extraction Layer                    │
│  ┌─────────────┐ ┌──────────────┐ ┌─────────────────┐  │
│  │   Typing    │ │    Touch     │ │   Usage Stats   │  │
│  │  Collector  │ │   Tracker    │ │    Helper       │  │
│  └─────────────┘ └──────────────┘ └─────────────────┘  │
└─────────────────────────┬───────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────┐
│              TensorFlow Lite Model                       │
│           (On-Device Inference Engine)                   │
└─────────────────────────┬───────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────┐
│               Stress Score & Dashboard                   │
│    ┌──────────┐  ┌────────────┐  ┌────────────────┐    │
│    │  Score   │  │   Trends   │  │    Insights    │    │
│    │  0-100   │  │Daily/Weekly│  │ Recommendations│    │
│    └──────────┘  └────────────┘  └────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
stree detection/
├── Project_Spec.md                    # Detailed project specification
│
├── ml_model/                          # Machine Learning Pipeline
│   ├── train_model.py                 # Scikit-learn Random Forest training
│   ├── convert_to_tflite.py           # TensorFlow Lite conversion script
│   ├── stress_rf_model.pkl            # Trained Random Forest model
│   ├── stress_model.keras             # Keras neural network model
│   └── stress_model.tflite            # Optimized TFLite model (5.77 KB)
│
└── android_app/                       # Native Android Application
    ├── build.gradle.kts               # Project-level Gradle config
    ├── settings.gradle.kts            # Gradle settings
    │
    └── app/
        ├── build.gradle.kts           # App-level dependencies
        ├── proguard-rules.pro         # ProGuard configuration
        │
        └── src/main/
            ├── AndroidManifest.xml    # App manifest
            │
            ├── assets/
            │   └── stress_model.tflite # ML model for inference
            │
            ├── java/com/example/stressdetection/
            │   ├── OnboardingActivity.kt   # First-time user onboarding
            │   ├── MainActivity.kt         # Main dashboard
            │   ├── FeatureCollector.kt     # Typing & touch feature extraction
            │   ├── StressInference.kt      # TFLite model wrapper
            │   ├── UsageStatsHelper.kt     # App usage pattern analysis
            │   ├── StressHistoryManager.kt # Historical data & trends
            │   └── DataCollectionService.kt # Background data collection
            │
            └── res/
                ├── layout/
                │   ├── activity_main.xml       # Dashboard layout
                │   ├── activity_onboarding.xml # Onboarding layout
                │   └── item_onboarding.xml     # Onboarding page item
                │
                ├── drawable/
                │   ├── button_primary.xml      # Gradient button
                │   ├── button_outline.xml      # Outline button
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

##  Getting Started

### Prerequisites

- **Android Studio** Arctic Fox or later
- **JDK 8+**
- **Python 3.8+** (for ML model training)
- **Android SDK 26+** (minSdk)

### Setup Android App

1. Clone the repository
2. Open `android_app/` folder in Android Studio
3. Sync Gradle dependencies
4. Build and run on device (emulator may not support all touch features)

### Train ML Model (Optional)

```bash
cd ml_model

# Install dependencies
pip install pandas scikit-learn tensorflow joblib

# Train Random Forest model
python train_model.py

# Convert to TFLite
python convert_to_tflite.py
```

---

##  Machine Learning Model

### Features Used

| Category | Feature | Description |
|----------|---------|-------------|
| Typing | `typing_speed` | Characters per second |
| Typing | `backspace_ratio` | Corrections per keystroke |
| Touch | `touch_pressure` | Average touch pressure (0-1) |
| Session | `session_length` | Current session duration |

### Model Architecture

- **Neural Network**: 4 → 32 → 16 → 8 → 1 (sigmoid)
- **Training Data**: Synthetic behavioral patterns
- **Output**: Stress probability (0.0 - 1.0)
- **Model Size**: ~5.77 KB (optimized TFLite)

### Stress Level Thresholds

| Score Range | Level | Color |
|-------------|-------|-------|
| 0-30 | Low | 🟢 Green |
| 31-65 | Medium | 🟡 Orange |
| 66-100 | High | 🔴 Red |

---

##  Privacy & Ethics

### Data Collection Principles

1. **Explicit Consent**: Users are fully informed during onboarding
2. **Local Processing**: All ML inference happens on-device
3. **No Raw Text Storage**: We only store derived metrics, never actual content
4. **User Control**: Clear data deletion option in settings
5. **Minimal Collection**: Only collect what's necessary for stress detection

### Permissions Required

| Permission | Purpose |
|------------|---------|
| `PACKAGE_USAGE_STATS` | App usage pattern analysis |
| `FOREGROUND_SERVICE` | Background monitoring |
| `POST_NOTIFICATIONS` | Status updates |

---

##  Screenshots

### Dashboard
- Large stress score display with circular ring indicator
- Quick metric cards (Typing Speed, Error Rate, Touch Pressure)
- Hourly stress chart with color-coded bars
- AI-generated insights and recommendations

### Onboarding
- 3-page introduction explaining the app's purpose
- Privacy-first messaging
- Permission request flow

---

## Tech Stack

| Component | Technology |
|-----------|------------|
| **Platform** | Android (API 26+) |
| **Language** | Kotlin |
| **UI** | XML Layouts + Material Design |
| **ML Runtime** | TensorFlow Lite 2.14 |
| **Training** | Python, TensorFlow, Scikit-learn |
| **Storage** | SharedPreferences + Gson |
| **Architecture** | MVVM-inspired |

---

##  License

This project is licensed under the MIT License.

---

##  Acknowledgments

- TensorFlow Lite team for on-device ML capabilities
- Material Design guidelines for modern UI patterns
- Research on behavioral stress indicators

---

**Built with for mental wellness**
