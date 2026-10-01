"""
Stress Detection Model Training Pipeline
=========================================
Generates research-informed synthetic behavioral data modeling patterns
documented in the StudentLife study (Wang et al., Dartmouth 2014) and
related mobile sensing stress detection literature.

Features (11):
  1. typing_speed_avg        - chars/sec average in window
  2. typing_speed_variance   - variance of typing speed
  3. backspace_rate           - corrections per keystroke
  4. app_switch_count_per_hour - app switching frequency
  5. screen_unlock_count      - unlocks per hour
  6. total_screen_time        - minutes of screen-on time per hour
  7. social_app_time_ratio    - fraction of screen time on social apps
  8. late_night_usage_minutes - usage between 11pm-4am
  9. movement_variance        - accelerometer-derived fidget score
  10. step_count_daily         - daily step count (÷1000 for scaling)
  11. call_sms_frequency_delta - change vs user baseline (z-score)

Labels: 0=Low, 1=Medium, 2=High stress (3-class)
"""

import numpy as np
import pandas as pd
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix
import joblib
import os


def generate_realistic_data(n_samples=3000, seed=42):
    """
    Generate synthetic behavioral data with realistic inter-feature
    correlations based on stress research findings.

    Key correlations modeled:
    - Higher stress → faster/more erratic typing, more backspaces
    - Higher stress → more app switching, more screen unlocks
    - Higher stress → more late-night usage
    - Higher stress → more fidgeting (movement variance)
    - Higher stress → fewer steps (sedentary behavior)
    - Higher stress → social isolation (fewer calls/SMS vs baseline)
    - Moderate stress → higher social media usage (doom-scrolling)
    """
    rng = np.random.default_rng(seed)

    # --- Generate latent stress factor (continuous, 0-1) ---
    # Use a mixture to get realistic class distribution (~40% Low, 35% Med, 25% High)
    components = rng.choice(3, size=n_samples, p=[0.40, 0.35, 0.25])
    latent_stress = np.zeros(n_samples)
    for i, c in enumerate(components):
        if c == 0:  # Low stress
            latent_stress[i] = rng.beta(2, 8)  # peaks near 0.2
        elif c == 1:  # Medium stress
            latent_stress[i] = rng.beta(5, 5)  # peaks near 0.5
        else:  # High stress
            latent_stress[i] = rng.beta(8, 2)  # peaks near 0.8

    # --- Generate features correlated with latent stress ---
    noise = lambda scale=0.1: rng.normal(0, scale, n_samples)

    # 1. Typing speed (chars/sec): stressed people type faster but erratically
    #    Baseline ~4-6 chars/sec, stressed goes up to 8+
    typing_speed_avg = 4.0 + latent_stress * 4.0 + noise(0.8)
    typing_speed_avg = np.clip(typing_speed_avg, 0.5, 12.0)

    # 2. Typing speed variance: higher stress → more erratic
    typing_speed_variance = 0.2 + latent_stress * 2.5 + noise(0.3)
    typing_speed_variance = np.clip(typing_speed_variance, 0.0, 4.0)

    # 3. Backspace rate: stressed → more errors
    backspace_rate = 0.05 + latent_stress * 0.25 + noise(0.04)
    backspace_rate = np.clip(backspace_rate, 0.0, 0.5)

    # 4. App switch count per hour: stressed → more switching (restlessness)
    app_switch_count = 3.0 + latent_stress * 20.0 + noise(3.0)
    app_switch_count = np.clip(app_switch_count, 0.0, 40.0)

    # 5. Screen unlock count per hour: stressed → compulsive checking
    screen_unlock_count = 2.0 + latent_stress * 8.0 + noise(1.5)
    screen_unlock_count = np.clip(screen_unlock_count, 0.0, 15.0)

    # 6. Total screen time (minutes per hour): higher with stress but saturates
    total_screen_time = 15.0 + latent_stress * 30.0 + noise(5.0)
    total_screen_time = np.clip(total_screen_time, 5.0, 58.0)

    # 7. Social app time ratio: moderate stress → higher (doom-scrolling), very high stress → lower
    #    Model as inverted-U: peaks around 0.5-0.6 stress
    social_app_ratio = 0.15 + 0.4 * np.sin(latent_stress * np.pi) + noise(0.08)
    social_app_ratio = np.clip(social_app_ratio, 0.0, 0.8)

    # 8. Late night usage (minutes between 11pm-4am): strongly correlates with stress
    late_night_usage = latent_stress * 60.0 + noise(10.0)
    # Low-stress people rarely use phone late at night
    late_night_mask = latent_stress < 0.3
    late_night_usage[late_night_mask] *= 0.3
    late_night_usage = np.clip(late_night_usage, 0.0, 120.0)

    # 9. Movement variance (fidgeting): higher stress → more fidgeting
    movement_variance = 0.1 + latent_stress * 1.5 + noise(0.2)
    movement_variance = np.clip(movement_variance, 0.0, 3.0)

    # 10. Daily step count (÷1000): stressed → more sedentary
    step_count_daily = 8.0 - latent_stress * 5.0 + noise(1.5)
    step_count_daily = np.clip(step_count_daily, 0.5, 15.0)

    # 11. Call/SMS frequency delta (z-score vs baseline): stressed → social withdrawal
    call_sms_delta = -latent_stress * 1.5 + noise(0.5)
    call_sms_delta = np.clip(call_sms_delta, -3.0, 3.0)

    # --- Generate labels ---
    # Use latent stress with slight randomization for label boundaries
    labels = np.zeros(n_samples, dtype=int)
    labels[latent_stress > 0.35] = 1  # Medium
    labels[latent_stress > 0.65] = 2  # High

    # --- Assemble DataFrame ---
    data = pd.DataFrame({
        'typing_speed_avg': typing_speed_avg,
        'typing_speed_variance': typing_speed_variance,
        'backspace_rate': backspace_rate,
        'app_switch_count_per_hour': app_switch_count,
        'screen_unlock_count': screen_unlock_count,
        'total_screen_time': total_screen_time,
        'social_app_time_ratio': social_app_ratio,
        'late_night_usage_minutes': late_night_usage,
        'movement_variance': movement_variance,
        'step_count_daily': step_count_daily,
        'call_sms_frequency_delta': call_sms_delta,
        'stress_level': labels
    })

    return data


def train_model():
    print("=" * 60)
    print("Stress Detection Model Training Pipeline")
    print("=" * 60)

    # Generate data
    print("\n[1/5] Generating research-informed synthetic data...")
    df = generate_realistic_data(n_samples=3000)

    print(f"  Dataset shape: {df.shape}")
    print(f"  Class distribution:")
    for label, name in [(0, "Low"), (1, "Medium"), (2, "High")]:
        count = (df['stress_level'] == label).sum()
        print(f"    {name}: {count} ({count/len(df)*100:.1f}%)")

    X = df.drop(columns=['stress_level'])
    y = df['stress_level']

    # Split data
    print("\n[2/5] Splitting data (70/15/15 train/val/test)...")
    X_train, X_temp, y_train, y_temp = train_test_split(
        X, y, test_size=0.30, random_state=42, stratify=y
    )
    X_val, X_test, y_val, y_test = train_test_split(
        X_temp, y_temp, test_size=0.50, random_state=42, stratify=y_temp
    )
    print(f"  Train: {len(X_train)}, Val: {len(X_val)}, Test: {len(X_test)}")

    # Train Gradient Boosting
    print("\n[3/5] Training Gradient Boosting Classifier...")
    model = GradientBoostingClassifier(
        n_estimators=200,
        max_depth=5,
        learning_rate=0.1,
        min_samples_split=10,
        min_samples_leaf=5,
        subsample=0.8,
        random_state=42
    )
    model.fit(X_train, y_train)

    # Cross-validation
    print("\n[4/5] Evaluating model...")
    cv_scores = cross_val_score(model, X_train, y_train, cv=5, scoring='accuracy')
    print(f"  Cross-validation accuracy: {cv_scores.mean():.4f} (±{cv_scores.std():.4f})")

    # Validation set
    val_preds = model.predict(X_val)
    val_acc = accuracy_score(y_val, val_preds)
    print(f"  Validation accuracy: {val_acc:.4f}")

    # Test set
    test_preds = model.predict(X_test)
    test_acc = accuracy_score(y_test, test_preds)
    print(f"  Test accuracy: {test_acc:.4f}")

    print("\n  Classification Report (Test Set):")
    target_names = ['Low', 'Medium', 'High']
    print(classification_report(y_test, test_preds, target_names=target_names))

    print("  Confusion Matrix:")
    cm = confusion_matrix(y_test, test_preds)
    print(f"  {cm}")

    # Feature importance
    print("\n  Feature Importances:")
    importances = sorted(
        zip(X.columns, model.feature_importances_),
        key=lambda x: x[1], reverse=True
    )
    for name, imp in importances:
        bar = "#" * int(imp * 50)
        print(f"    {name:30s} {imp:.4f} {bar}")

    # Save model
    print("\n[5/5] Saving model...")
    model_path = os.path.join(os.path.dirname(__file__), "stress_gb_model.pkl")
    joblib.dump(model, model_path)
    print(f"  Model saved to {model_path}")

    # Save feature names and normalization stats for the converter
    stats = {
        'feature_names': list(X.columns),
        'means': X_train.mean().to_dict(),
        'stds': X_train.std().to_dict(),
        'n_classes': 3,
        'class_names': target_names
    }
    stats_path = os.path.join(os.path.dirname(__file__), "feature_stats.pkl")
    joblib.dump(stats, stats_path)
    print(f"  Feature stats saved to {stats_path}")

    print("\n" + "=" * 60)
    print("Training complete!")
    print("=" * 60)

    return model, stats


if __name__ == "__main__":
    train_model()
