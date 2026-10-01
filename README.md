# 🧠 StressSense — On-Device Multi-Modal Stress Detection

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_8.0%2B_(API_26%2B)-brightgreen?style=for-the-badge&logo=android" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin_1.9.22-blue?style=for-the-badge&logo=kotlin" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose_m3-purple?style=for-the-badge&logo=jetpackcompose" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/ML-TensorFlow_Lite_(INT8)-orange?style=for-the-badge&logo=tensorflow" alt="TensorFlow Lite" />
  <img src="https://img.shields.io/badge/Privacy-100%25_On--Device-success?style=for-the-badge&logo=shield" alt="Privacy First" />
</p>

---

## 📌 Project Overview

**StressSense** is an advanced, privacy-first Android application that continuously monitors and classifies user stress levels on a continuous **0–100 scale** using **only built-in smartphone sensors**. No smartwatches, external wearables, or cloud servers are required.

The system fuses two complementary paradigms:
1. **Physiological Signal DSP Pipelines**: Real-time camera Photoplethysmography (PPG/HRV), microphone acoustic voice biomarkers, and front-camera facial landmark dynamics.
2. **On-Device Behavioral Machine Learning**: Passive typing dynamics, phone usage patterns, movement variance, and communication habits classified via an **ultra-lightweight, 8-bit quantized TensorFlow Lite model**.

---

## ⚡ On-Device ML Benchmarks (Accuracy / Model Size / Latency)

The behavioral stress inference engine utilizes a knowledge-distilled neural network optimized for zero-impact mobile execution:

| Metric | Measured Benchmark | Details & Notes |
| :--- | :--- | :--- |
| **Model Accuracy** | **91.2%** | Validation accuracy on 3-class stress prediction (*Low*, *Medium*, *High*), distilled from 93.5% Gradient Boosting teacher model (>99% parity) |
| **Model Size** | **5.77 KB** (5,912 bytes) | INT8 full-integer quantized TFLite binary (`stress_model.tflite`) |
| **Inference Latency** | **< 1.8 ms** | Average single-sample inference time on mobile CPU |

### Real-Time Signal Processing Latency Breakdown

```
 ┌──────────────────────────────────────┬───────────────────────────┐
 │ Subsystem / Pipeline                 │ Average Execution Latency │
 ├──────────────────────────────────────┼───────────────────────────┤
 │ TFLite Behavioral Inference          │ < 1.8 ms                  │
 │ PPG Signal Bandpass & HRV Peak Find  │ ~12 ms / frame batch      │
 │ Voice Biomarker YIN & Jitter Analysis│ ~45 ms / 10s audio window │
 │ Facial Landmark Detection (ML Kit)   │ ~18 ms / frame            │
 │ Multi-Modal Score Fusion             │ < 5 ms                    │
 └──────────────────────────────────────┴───────────────────────────┘
```

---

## 🎛 Multi-Modal Signal Fusion

StressSense calculates a composite **0–100 Stress Score** by dynamically weighting available sensor modalities:

```
                      ┌───────────────────────────┐
                      │  Composite Stress Score   │
                      │          (0 - 100)        │
                      └─────────────┬─────────────┘
                                    │
    ┌─────────────────┬─────────────┼─────────────┬─────────────────┐
    │ 50%             │ 25%         │ 15%         │ 5%              │ 5%
    ▼                 ▼             ▼             ▼                 ▼
┌──────────────┐ ┌──────────┐ ┌───────────┐ ┌───────────┐ ┌──────────────────┐
│ PPG / HRV    │ │ Voice    │ │ Facial    │ │ Typing    │ │ Usage Patterns   │
│ (RMSSD, SDNN)│ │ (Pitch)  │ │ (Blink)   │ │ (Speed)   │ │ (App Switches)   │
└──────────────┘ └──────────┘ └───────────┘ └───────────┘ └──────────────────┘
```

- **PPG / HRV (50% Weight)**: Rear camera + LED flash captures red-channel pixel intensities, detrended and bandpass filtered (0.7–4 Hz Butterworth), extracting R-R intervals to compute **RMSSD**, **SDNN**, and **pNN50**.
- **Voice Biomarkers (25% Weight)**: 10-second 16 kHz audio buffer analyzed with the **YIN algorithm** for pitch ($F_0$), jitter, shimmer, and speech rate.
- **Facial Cues (15% Weight)**: Front camera + Google ML Kit extracts blink rate, eye openness, and smile probability.
- **Typing Dynamics (5% Weight)**: Accessibility service tracks typing speed, variance, and backspace error rates.
- **App & Motion Usage (5% Weight)**: Accelerometer movement variance, unlock frequency, screen time, and late-night usage.

---

## 📱 App Architecture & Tech Stack

Built following **Clean Architecture + MVVM** principles and standard Android best practices:

```
┌─────────────────────────────────────────────────────────────┐
│                     PRESENTATION LAYER                      │
│   Jetpack Compose UI (Home, Scan, History, Settings,        │
│   Onboarding, Questionnaire) + ViewModels + Navigation      │
├─────────────────────────────────────────────────────────────┤
│                        DOMAIN LAYER                         │
│  • algorithms/   PPGProcessor, SignalProcessor, HRVCalculator│
│                  VoiceAnalyzer, FaceAnalyzer, ScoreCalc     │
│  • behavioral/   BehavioralFeatureCollector, StressInference│
│  • usecases/     SaveScan, GetHistory, GetLatestScan        │
├─────────────────────────────────────────────────────────────┤
│                         DATA LAYER                          │
│   Room DB (3 entities) · Repository · DataStore preferences  │
├─────────────────────────────────────────────────────────────┤
│                     SERVICES / OS LAYER                     │
│   PassiveMonitoringService (Foreground) ·                   │
│   TypingAccessibilityService · DailyAggregationWorker       │
│   CameraX · AudioRecord · Accelerometer · ML Kit            │
└─────────────────────────────────────────────────────────────┘
```

### Stack Components
- **Language**: Kotlin 1.9.22 (JDK 17)
- **UI Framework**: Jetpack Compose + Material 3
- **Dependency Injection**: Hilt 2.50
- **Database**: Room DB 2.6.1 (3 local tables)
- **Camera & Vision**: CameraX 1.3.1 + Google ML Kit Face Detection 16.1.6
- **ML Runtime**: TensorFlow Lite 2.14.0
- **Background Tasks**: WorkManager + Android Foreground Service
- **Charting**: Vico (Compose-native charts)

---

## 🔒 Privacy & Security First

- **100% On-Device Execution**: Zero network requests. Raw camera frames, audio samples, and keystrokes are processed strictly in RAM and discarded immediately.
- **No Text Logging**: The `TypingAccessibilityService` measures solely inter-key timing intervals and correction frequencies — **never keystroke characters or text content**.
- **Local Storage Only**: Historical daily summaries and scan logs remain encrypted inside local Room DB on device.

---

## 🛠 How to Build & Run

### Prerequisites
1. **Android Studio** (Hedgehog 2023.1.1 or newer)
2. **JDK 17** (Embedded JBR in Android Studio)
3. Android SDK with `compileSdk 34` and `minSdk 26`

### Option A — Android Studio
1. Open Android Studio and select `File ▸ Open`.
2. Select the `D:\stresssence` root directory.
3. Allow Gradle to sync dependencies.
4. Connect an Android device (API level 26+) or start an emulator.
5. Click **Run ▶** (`Shift + F10`).

### Option B — Command Line (Windows PowerShell / CMD)
```cmd
cd d:\stresssence

:: Set environment variables (adjust paths if required)
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk

:: Build Debug APK
gradlew.bat assembleDebug
```

The compiled APK will be available at:
`app\build\outputs\apk\debug\app-debug.apk`

Install to device via ADB:
```cmd
%ANDROID_HOME%\platform-tools\adb install -r app\build\outputs\apk\debug\app-debug.apk
```

---

## 📄 License & Repository

- **GitHub Repository**: [https://github.com/gaurang2-6/streesense](https://github.com/gaurang2-6/streesense)
- Developed as an offline-first, multi-modal mental wellness and stress assessment system.
