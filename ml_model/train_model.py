import numpy as np
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, classification_report
import joblib

# ---------------------------------------------------------
# Synthetic Data Generation (for demonstration)
# In a real scenario, this would be replaced by data collection
# ---------------------------------------------------------
def generate_synthetic_data(n_samples=1000):
    np.random.seed(42)
    
    # Feature: Typing Speed (chars per second)
    # Stressed users might type faster/erratic or slower/hesitant. Let's assume erratic/fast.
    typing_speed = np.random.normal(loc=5.0, scale=1.5, size=n_samples)
    
    # Feature: Backspace Ratio (corrections per char)
    # Stressed users make more mistakes
    backspace_ratio = np.random.beta(a=2, b=10, size=n_samples)
    
    # Feature: Touch Pressure (normalized 0-1)
    # Stressed users might press harder
    touch_pressure = np.random.normal(loc=0.6, scale=0.2, size=n_samples)
    
    # Feature: Session Length (seconds)
    # Stressed users might have short, check-y sessions
    session_length = np.random.exponential(scale=60, size=n_samples)
    
    # Generate labels based on some linear combination + noise
    # Stress score calculation (hidden ground truth)
    stress_score = (
        (typing_speed * 0.5) + 
        (backspace_ratio * 10) + 
        (touch_pressure * 3) - 
        (session_length * 0.01)
    )
    
    # Normalize and threshold for labels
    # 0: Low Stress, 1: High Stress
    threshold = np.percentile(stress_score, 70) # Top 30% are stressed
    labels = (stress_score > threshold).astype(int)
    
    data = pd.DataFrame({
        'typing_speed': typing_speed,
        'backspace_ratio': backspace_ratio,
        'touch_pressure': touch_pressure,
        'session_length': session_length,
        'is_stressed': labels
    })
    
    return data

# ---------------------------------------------------------
# Training Pipeline
# ---------------------------------------------------------
def train_model():
    print("Generating synthetic data...")
    df = generate_synthetic_data()
    
    X = df.drop(columns=['is_stressed'])
    y = df['is_stressed']
    
    print(f"Dataset shape: {X.shape}")
    
    # Split data
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)
    
    # Initialize Model (Random Forest is robust for this type of tabular data)
    model = RandomForestClassifier(n_estimators=100, random_state=42, max_depth=10)
    
    # Train
    print("Training model...")
    model.fit(X_train, y_train)
    
    # Evaluate
    preds = model.predict(X_test)
    acc = accuracy_score(y_test, preds)
    print(f"Model Accuracy: {acc:.4f}")
    print("\nClassification Report:")
    print(classification_report(y_test, preds))
    
    # Export
    model_filename = "stress_rf_model.pkl"
    joblib.dump(model, model_filename)
    print(f"Model saved to {model_filename}")
    
    # Feature Importance
    print("\nFeature Importances:")
    for name, imp in zip(X.columns, model.feature_importances_):
        print(f"{name}: {imp:.4f}")

if __name__ == "__main__":
    train_model()
