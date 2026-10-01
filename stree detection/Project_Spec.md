# Stress Detection App (Interaction-Based) - Project Specification

## 1. Define the Core Idea (What You Are Building)
**Objective:** Detect user stress levels by analyzing behavioral interaction patterns.
**Output:** Stress Level (Low / Medium / High), optional score (0–100), trends over time.

## 2. Interaction Signals (No Hardware)
**A. Typing Behavior:** Speed, backspace frequency, key press duration, error rate, pauses.
**B. Touch & Gesture Patterns:** Pressure, duration, swipe speed, scroll acceleration, micro-movements.
**C. Usage Patterns:** Unlock frequency, session length, app switching, night usage.
**D. App Navigation Behavior:** Rapid switching, reopening screens, incomplete tasks.

## 3. Data Collection Layer (App Side)
**Android:**
- Touch listeners (MotionEvent)
- Keyboard listeners
- App usage stats (UsageStatsManager)
- Screen on/off events
- Accessibility services (optional)

## 4. Feature Engineering
**Typing:** `avg_typing_speed`, `backspace_ratio`, `mean_key_hold_time`.
**Touch:** `avg_touch_pressure`, `gesture_jerk`.
**Usage:** `unlock_count_per_hour`, `avg_session_length`, `night_usage_ratio`.

## 5. Stress Labeling Strategy
**Option 1:** Self-Reported Stress (Recommended).
**Option 2:** Passive Labeling (Time of day, known stress periods).

## 6. Machine Learning Model
**Models:** Logistic Regression, Random Forest, XGBoost.
**Input:** Features from step 4.
**Output:** Stress probability/class.

## 7. Model Training Pipeline
Collect -> Normalize -> Handle missing -> Train -> Validate -> Export.

## 8. On-Device vs Cloud Inference
**On-Device:** TensorFlow Lite (Android), Core ML (iOS).

## 9. Stress Score Logic
0–30: Low, 31–65: Moderate, 66–100: High.

## 10. UI / UX
Onboarding, Passive tracking, Dashboard, Trends.

## 11. Ethical & Privacy
Explicit consent, Local processing, No raw text storage.

## 14. Tech Stack
**Android:** Kotlin
**ML:** Python, TFLite
