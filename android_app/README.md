
# Stress Detection - Android Project

This folder contains the source code for the Android application.

## Structure
- `app/src/main/java`: Kotlin source code
- `app/src/main/res`: UI Resources
- `build.gradle.kts`: Build configuration

## Key Components to Implement
1. `MainActivity.kt`: Dashboard and entry point.
2. `DataCollectionService.kt`: Background service for listening to system events (Interaction Signals).
3. `StressInference.kt`: Helper to run TFLite model on collected data.
4. `OnboardingActivity.kt`: Permissions and user consent.
