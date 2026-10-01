"""
TFLite Conversion Pipeline
===========================
Takes the trained Gradient Boosting model and creates a TFLite model by:
1. Using the GB model to generate soft-label training data
2. Training a compact neural network on those soft labels (knowledge distillation)
3. Converting to TFLite with quantization
4. Validating the TFLite model matches the original
"""

import numpy as np
import tensorflow as tf
import joblib
import os
import shutil
import time

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))


def create_stress_model(n_features=11, n_classes=3):
    """Create a compact neural network for on-device inference."""
    model = tf.keras.Sequential([
        tf.keras.layers.InputLayer(input_shape=(n_features,)),
        tf.keras.layers.Dense(64, activation='relu'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(32, activation='relu'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.Dense(16, activation='relu'),
        tf.keras.layers.Dense(n_classes, activation='softmax')  # 3-class output
    ])

    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    return model


def generate_distillation_data(gb_model, stats, n_samples=5000, seed=42):
    """
    Generate training data for the neural network using the GB model
    as the teacher (knowledge distillation approach).
    """
    rng = np.random.default_rng(seed)
    means = stats['means']
    stds = stats['stds']
    feature_names = stats['feature_names']

    # Generate data spanning the full feature space
    X = np.zeros((n_samples, len(feature_names)))
    for i, feat in enumerate(feature_names):
        mean = means[feat]
        std = stds[feat]
        X[:, i] = rng.normal(mean, std * 1.5, n_samples)

    # Normalize for the neural network
    X_norm = np.zeros_like(X)
    for i, feat in enumerate(feature_names):
        X_norm[:, i] = (X[:, i] - means[feat]) / (stds[feat] + 1e-8)

    # Get GB model predictions as labels
    y = gb_model.predict(X)

    return X_norm.astype(np.float32), y.astype(np.int64)


def train_and_convert():
    print("=" * 60)
    print("TFLite Conversion Pipeline")
    print("=" * 60)

    # Load trained GB model and stats
    print("\n[1/6] Loading trained model...")
    gb_model = joblib.load(os.path.join(SCRIPT_DIR, "stress_gb_model.pkl"))
    stats = joblib.load(os.path.join(SCRIPT_DIR, "feature_stats.pkl"))
    n_features = len(stats['feature_names'])
    n_classes = stats['n_classes']
    print(f"  Features: {n_features}, Classes: {n_classes}")
    print(f"  Feature names: {stats['feature_names']}")

    # Generate distillation data
    print("\n[2/6] Generating distillation training data...")
    X, y = generate_distillation_data(gb_model, stats, n_samples=8000)
    print(f"  Generated {len(X)} samples")
    print(f"  Class distribution: {np.bincount(y)}")

    # Split
    split = int(0.85 * len(X))
    X_train, X_test = X[:split], X[split:]
    y_train, y_test = y[:split], y[split:]

    # Create and train neural network
    print("\n[3/6] Training neural network (knowledge distillation)...")
    tf_model = create_stress_model(n_features, n_classes)
    tf_model.summary()

    history = tf_model.fit(
        X_train, y_train,
        validation_data=(X_test, y_test),
        epochs=80,
        batch_size=64,
        verbose=2,
        callbacks=[
            tf.keras.callbacks.EarlyStopping(
                patience=10, restore_best_weights=True
            ),
            tf.keras.callbacks.ReduceLROnPlateau(
                factor=0.5, patience=5, min_lr=1e-5
            )
        ]
    )

    # Evaluate
    print("\n[4/6] Evaluating neural network...")
    loss, accuracy = tf_model.evaluate(X_test, y_test, verbose=0)
    print(f"  Test accuracy: {accuracy:.4f}")

    # Save Keras model
    keras_path = os.path.join(SCRIPT_DIR, "stress_model.keras")
    tf_model.save(keras_path)
    print(f"  Saved Keras model to {keras_path}")

    # Convert to TFLite
    print("\n[5/6] Converting to TFLite...")
    converter = tf.lite.TFLiteConverter.from_keras_model(tf_model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    # Provide representative dataset for full integer quantization
    def representative_dataset():
        for i in range(min(500, len(X_train))):
            yield [X_train[i:i+1]]
    converter.representative_dataset = representative_dataset
    tflite_model = converter.convert()

    # Save TFLite model
    tflite_path = os.path.join(SCRIPT_DIR, "stress_model.tflite")
    with open(tflite_path, "wb") as f:
        f.write(tflite_model)
    print(f"  Saved TFLite model to {tflite_path}")
    print(f"  TFLite model size: {len(tflite_model) / 1024:.2f} KB")

    # Validate TFLite model
    print("\n[6/6] Validating TFLite model...")
    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    print(f"  Input shape: {input_details[0]['shape']}, dtype: {input_details[0]['dtype']}")
    print(f"  Output shape: {output_details[0]['shape']}, dtype: {output_details[0]['dtype']}")

    # Test predictions on a few samples & measure inference latency
    correct = 0
    n_test = min(100, len(X_test))
    
    # Warmup
    test_input = X_test[0:1].astype(np.float32)
    interpreter.set_tensor(input_details[0]['index'], test_input)
    interpreter.invoke()

    # Benchmark Latency over 1,000 runs
    start_time = time.perf_counter()
    n_benchmark = 1000
    for _ in range(n_benchmark):
        interpreter.set_tensor(input_details[0]['index'], test_input)
        interpreter.invoke()
        _ = interpreter.get_tensor(output_details[0]['index'])
    avg_latency_ms = ((time.perf_counter() - start_time) / n_benchmark) * 1000.0

    for i in range(n_test):
        test_input = X_test[i:i+1].astype(np.float32)
        interpreter.set_tensor(input_details[0]['index'], test_input)
        interpreter.invoke()
        output = interpreter.get_tensor(output_details[0]['index'])
        pred = np.argmax(output[0])
        if pred == y_test[i]:
            correct += 1

    tflite_acc = correct / n_test
    tflite_size_kb = len(tflite_model) / 1024.0

    print(f"  TFLite validation accuracy: {tflite_acc * 100:.2f}%")
    print(f"  TFLite model size: {tflite_size_kb:.2f} KB")
    print(f"  Average inference latency: {avg_latency_ms:.3f} ms / run")

    print("\n  " + "=" * 45)
    print("  ON-DEVICE MODEL BENCHMARK SUMMARY")
    print("  " + "=" * 45)
    print(f"  • Model Accuracy  : {tflite_acc * 100:.1f}%")
    print(f"  • Model Size      : {tflite_size_kb:.2f} KB")
    print(f"  • Inference Latency: {avg_latency_ms:.2f} ms")
    print("  " + "=" * 45)

    # Test with sample inputs for each class
    class_names = stats['class_names']
    print("\n  Sample predictions:")
    samples = [
        ("Relaxed student", [4.0, 0.3, 0.05, 3.0, 2.0, 15.0, 0.15, 0.0, 0.2, 8.0, 0.5]),
        ("Moderate stress", [6.0, 1.2, 0.15, 12.0, 5.0, 35.0, 0.45, 20.0, 0.8, 5.0, -0.5]),
        ("High stress",     [8.0, 2.5, 0.30, 25.0, 10.0, 50.0, 0.30, 60.0, 1.8, 2.0, -1.5]),
    ]
    for name, raw_features in samples:
        # Normalize
        norm = []
        for j, feat_name in enumerate(stats['feature_names']):
            norm.append((raw_features[j] - stats['means'][feat_name]) / (stats['stds'][feat_name] + 1e-8))
        test_input = np.array([norm], dtype=np.float32)
        interpreter.set_tensor(input_details[0]['index'], test_input)
        interpreter.invoke()
        output = interpreter.get_tensor(output_details[0]['index'])
        probs = output[0]
        pred_class = class_names[np.argmax(probs)]
        print(f"    {name:20s} → {pred_class} (probs: L={probs[0]:.2f}, M={probs[1]:.2f}, H={probs[2]:.2f})")

    # Copy to Android assets
    android_assets = os.path.join(SCRIPT_DIR, "..", "android_app", "app", "src", "main", "assets")
    if os.path.exists(android_assets):
        dest = os.path.join(android_assets, "stress_model.tflite")
        shutil.copy2(tflite_path, dest)
        print(f"\n  Copied TFLite model to Android assets: {dest}")

        # Also save normalization stats as a simple text file for the Android app
        stats_dest = os.path.join(android_assets, "feature_stats.txt")
        with open(stats_dest, 'w') as f:
            f.write(f"n_features={n_features}\n")
            f.write(f"n_classes={n_classes}\n")
            for feat_name in stats['feature_names']:
                m = stats['means'][feat_name]
                s = stats['stds'][feat_name]
                f.write(f"{feat_name}={m:.6f},{s:.6f}\n")
        print(f"  Saved feature stats to {stats_dest}")

    print("\n" + "=" * 60)
    print("Conversion complete!")
    print("=" * 60)

    return tflite_path


if __name__ == "__main__":
    train_and_convert()
