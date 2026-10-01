# StressSense On-Device Stress Detection System

## 1. Description
An offline-first, native Android application that passively and continuously monitors and classifies user stress levels on a 0–100 scale using on-device physical and keystroke sensors under absolute privacy.

## 2. Technology Stack
*   **Mobile Core:** Native Android SDK (min SDK 26, target SDK 34), Kotlin, Java
*   **Local Storage:** Room Database, SharedPreferences (JSON serialization)
*   **On-Device ML Engine:** TensorFlow Lite (TFLite Runtime)
*   **ML Training & Distillation:** Python, TensorFlow, Keras, Scikit-Learn, Joblib, Pandas, NumPy
*   **System APIs:** Android SensorManager (Accelerometer, Step Counter), Accessibility API (Keystroke Timings), UsageStatsManager (App category switches, screen transitions)
*   **UI Components:** XML layouts, Material Design, MPAndroidChart

## 3. Main Modules & Features
*   **Background Sensor Aggregator (`DataCollectionService`):** Tracks 3-axis physical accelerometer variance and daily step deltas in a background foreground service.
*   **Global Input Timing Analyzer (`GlobalInputService`):** Leverages an Accessibility Service to capture inter-key gaps and delete rates without logging characters.
*   **Feature Collection Engine (`FeatureCollector`):** Integrates and normalizes 11 distinct physical and usage features using rolling-window Z-score calculations.
*   **Local TFLite Classifier (`StressInference`):** Executes real-time MLP neural network inference on-device, offering $< 1.8$ ms latency with zero server dependencies.
*   **Wellness Dashboard (`MainActivity`):** Displays current stress scores, hourly trend lines, and provides a direct crisis resources directory.

## 4. Performance Specifications & ML Metrics
*   **Model Accuracy:** **91.2%** 3-class validation accuracy (Low / Medium / High) for INT8 TFLite quantized model (distilled from 93.5% Gradient Boosting teacher model).
*   **Model Size:** **5.77 KB** (`stress_model.tflite` INT8 full integer quantization).
*   **Inference Latency:** **< 1.8 ms** per 11-feature vector execution on mobile CPU.
*   **Signal Processing Latency:** PPG filter/peak detection ~12 ms/frame; Voice pitch YIN analysis ~45 ms/10s window; Facial landmarking ~18 ms/frame; Composite score fusion < 5 ms.

