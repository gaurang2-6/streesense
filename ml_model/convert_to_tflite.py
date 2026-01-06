import numpy as np
import tensorflow as tf
from sklearn.ensemble import RandomForestClassifier
import joblib

# Load the trained sklearn model
model = joblib.load("stress_rf_model.pkl")

# Create a simple TensorFlow model that mimics the Random Forest behavior
# For production, we train a neural network that can be converted to TFLite

def create_stress_model():
    """Create a simple neural network for stress detection"""
    model = tf.keras.Sequential([
        tf.keras.layers.InputLayer(input_shape=(4,)),  # 4 features
        tf.keras.layers.Dense(32, activation='relu'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(16, activation='relu'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.Dense(8, activation='relu'),
        tf.keras.layers.Dense(1, activation='sigmoid')  # Binary classification
    ])
    
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss='binary_crossentropy',
        metrics=['accuracy']
    )
    return model

def generate_training_data(n_samples=2000):
    """Generate synthetic training data"""
    np.random.seed(42)
    
    typing_speed = np.random.normal(loc=5.0, scale=1.5, size=n_samples)
    backspace_ratio = np.random.beta(a=2, b=10, size=n_samples)
    touch_pressure = np.random.normal(loc=0.6, scale=0.2, size=n_samples)
    session_length = np.random.exponential(scale=60, size=n_samples)
    
    # Normalize features
    typing_speed = (typing_speed - 5.0) / 1.5
    backspace_ratio = backspace_ratio * 5  # Scale up
    touch_pressure = (touch_pressure - 0.6) / 0.2
    session_length = (session_length - 60) / 60
    
    X = np.column_stack([typing_speed, backspace_ratio, touch_pressure, session_length])
    
    # Generate labels
    stress_score = (
        (typing_speed * 0.5) + 
        (backspace_ratio * 0.3) + 
        (touch_pressure * 0.3) - 
        (session_length * 0.1)
    )
    threshold = np.percentile(stress_score, 70)
    y = (stress_score > threshold).astype(np.float32)
    
    return X.astype(np.float32), y

def train_and_convert():
    print("Generating training data...")
    X, y = generate_training_data()
    
    # Split
    split = int(0.8 * len(X))
    X_train, X_test = X[:split], X[split:]
    y_train, y_test = y[:split], y[split:]
    
    print("Creating TensorFlow model...")
    tf_model = create_stress_model()
    
    print("Training model...")
    history = tf_model.fit(
        X_train, y_train,
        validation_data=(X_test, y_test),
        epochs=50,
        batch_size=32,
        verbose=1
    )
    
    # Evaluate
    loss, accuracy = tf_model.evaluate(X_test, y_test)
    print(f"\nTest Accuracy: {accuracy:.4f}")
    
    # Save Keras model
    tf_model.save("stress_model.keras")
    print("Saved Keras model to stress_model.keras")
    
    # Convert to TFLite
    print("\nConverting to TFLite...")
    converter = tf.lite.TFLiteConverter.from_keras_model(tf_model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()
    
    # Save TFLite model
    tflite_path = "stress_model.tflite"
    with open(tflite_path, "wb") as f:
        f.write(tflite_model)
    
    print(f"Saved TFLite model to {tflite_path}")
    print(f"TFLite model size: {len(tflite_model) / 1024:.2f} KB")
    
    # Test TFLite model
    print("\nTesting TFLite model...")
    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()
    
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    # Test with sample input
    test_input = np.array([[0.5, 0.3, 0.7, -0.2]], dtype=np.float32)
    interpreter.set_tensor(input_details[0]['index'], test_input)
    interpreter.invoke()
    output = interpreter.get_tensor(output_details[0]['index'])
    print(f"Sample prediction: {output[0][0]:.4f}")
    
    return tflite_path

if __name__ == "__main__":
    train_and_convert()
