# Project Spec: No-Hardware Stress Detection App for Students (Android)

## Overview
Build an Android app (Kotlin) that predicts student stress levels using **passive phone usage data only** — no wearables or external sensors. The app collects behavioral signals, runs a lightweight on-device model (TFLite) for instant predictions, and optionally syncs to a cloud backend for deeper analysis and model retraining.

This is a **mental-health-adjacent** app. It is NOT a diagnostic tool. Every screen showing a stress prediction must include a disclaimer and a link to mental health resources.

---

## 1. Data Sources (Android APIs, No Hardware)

Collect the following signals passively:

| Signal | API | Notes |
|---|---|---|
| App usage / switching frequency | `UsageStatsManager` | Requires `PACKAGE_USAGE_STATS` permission (user must grant via settings) |
| Screen on/off events, unlock frequency | `BroadcastReceiver` for `ACTION_SCREEN_ON/OFF`, `ACTION_USER_PRESENT` | |
| Typing dynamics (speed, backspace rate, pause patterns) | `AccessibilityService` or custom keyboard (IME) | AccessibilityService is easier MVP; IME gives richer data |
| Notification response time | `NotificationListenerService` | |
| Accelerometer/gyroscope (fidgeting, movement) | `SensorManager` (TYPE_ACCELEROMETER, TYPE_GYROSCOPE) | Sample at low frequency (1Hz) to save battery |
| Call/SMS frequency changes | `CallLog`, `Telephony` content providers | Requires runtime permissions |
| Time-of-day usage patterns / late-night activity | Derived from screen events + timestamps | |
| Step count / mobility proxy | `Sensor.TYPE_STEP_COUNTER` | |

**Privacy requirement:** All raw data stays on-device by default. Only aggregated feature vectors (not raw logs) are sent to the cloud, and only if the user opts in.

---

## 2. Feature Engineering Pipeline

Aggregate raw signals into rolling-window features (e.g., 1-hour and 24-hour windows):

- `typing_speed_avg`, `typing_speed_variance`
- `backspace_rate`
- `app_switch_count_per_hour`
- `screen_unlock_count`
- `total_screen_time`
- `social_app_time_ratio` (social media vs total)
- `late_night_usage_minutes` (usage between 11pm–4am)
- `notification_response_time_avg`
- `movement_variance` (accelerometer-derived fidget score)
- `step_count_daily`
- `call_sms_frequency_delta` (change vs. user's baseline)

Store these as a feature vector per time window in local SQLite/Room DB.

---

## 3. Model

### Training (offline, done once by agent)
- Use the **StudentLife dataset** (Dartmouth, public) — it has exactly these feature types plus self-reported PSS (Perceived Stress Scale) labels.
- Map StudentLife's raw sensor logs to the feature schema above.
- Train a **Gradient Boosting classifier** (XGBoost/LightGBM) for stress level (Low / Medium / High) — interpretable and proven to work well on this kind of tabular behavioral data.
- Export to **TFLite** format for on-device inference.
- Ship this pre-trained model bundled in the app (`assets/stress_model.tflite`).

### On-device inference
- Run TFLite model every hour (or on-demand) using the latest feature vector.
- Output: stress probability (Low/Medium/High) + confidence score.
- Store prediction history locally for trend visualization.

### Cloud component (hybrid)
- Lightweight backend (FastAPI or Node) that accepts opted-in aggregated feature vectors + predictions.
- Used for: (a) generating weekly/monthly trend reports, (b) future model retraining on aggregated anonymized data, (c) optional LLM-based personalized insights (e.g., "Your stress tends to spike on Sunday nights — possibly exam prep related").
- No raw keystrokes, messages, or call content ever leave the device.

---

## 4. App Structure (Android, Kotlin)

```
app/
├── data/
│   ├── collectors/        # UsageStatsCollector, SensorCollector, AccessibilityCollector, etc.
│   ├── db/                 # Room DB for feature vectors + predictions
│   └── repository/
├── ml/
│   ├── StressModel.kt      # TFLite interpreter wrapper
│   └── FeatureExtractor.kt # Converts raw collector data -> feature vector
├── service/
│   └── BackgroundCollectionService.kt  # Foreground service, runs collectors
├── ui/
│   ├── dashboard/           # Stress trend graphs, current status
│   ├── onboarding/          # Permission requests, consent, disclaimers
│   ├── settings/            # Cloud sync opt-in/out, data export/delete
│   └── resources/           # Mental health resources screen
└── cloud/
    └── ApiClient.kt          # Optional sync to backend
```

---

## 5. Required Permissions & Consent Flow

- Onboarding screen must clearly explain what data is collected and why.
- Explicit opt-in for each data category (user can disable typing tracking, call logs, etc. individually).
- `PACKAGE_USAGE_STATS` — special permission, deep-link to settings.
- Runtime permissions: `READ_CALL_LOG`, `READ_SMS` (optional, off by default), `BODY_SENSORS` (if using step counter via health APIs), `ACTIVITY_RECOGNITION`.
- Provide a "Delete all my data" button (local + cloud).

---

## 6. UI Requirements

1. **Onboarding**: consent screens, permission requests, brief explanation of how predictions work.
2. **Dashboard**: current stress level (Low/Medium/High) with confidence, daily/weekly trend chart.
3. **Insights**: feature breakdown (e.g., "Screen time up 40% vs your average this week").
4. **Resources screen**: mental health helplines/resources (static content, always accessible).
5. **Settings**: toggle individual data collectors, cloud sync opt-in, data export/delete.

**Disclaimer text** (must appear on dashboard and in onboarding):
> "This app provides an experimental, non-clinical estimate of stress based on phone usage patterns. It is not a medical diagnosis. If you're struggling, please reach out to a counselor or mental health professional."

---

## 7. MVP Build Order (for the agent)

1. Set up Android project (Kotlin, min SDK 26+).
2. Implement consent/onboarding flow with permission requests.
3. Build data collectors (start with: UsageStatsManager, screen on/off, accelerometer).
4. Build FeatureExtractor to compute rolling-window features into Room DB.
5. Convert StudentLife dataset → train Gradient Boosting model offline → export TFLite.
6. Integrate TFLite model for on-device inference, run hourly via WorkManager.
7. Build dashboard UI showing current prediction + trend chart.
8. Add resources screen + disclaimers.
9. (Optional/Phase 2) Build backend API + cloud sync with opt-in toggle.
10. (Optional/Phase 2) Add typing dynamics via AccessibilityService.

---

## 8. Tech Stack Summary

- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0)
- **Local DB**: Room (SQLite)
- **Background work**: WorkManager (hourly feature extraction + inference)
- **ML**: TensorFlow Lite (on-device), trained offline with XGBoost/LightGBM → converted to TFLite
- **Charts**: MPAndroidChart or Vico
- **Backend (Phase 2)**: FastAPI + PostgreSQL, hosted on Render/Railway
- **Dataset for pre-training**: StudentLife (Dartmouth, public dataset)

---

## 9. Non-Negotiables

- No raw personal content (messages, call audio, keystroke content) ever leaves the device.
- All cloud sync is opt-in, with clear toggle in settings and a delete-all-data option.
- Stress predictions always shown with a non-diagnostic disclaimer.
- Resources/helpline screen always accessible from main navigation.
