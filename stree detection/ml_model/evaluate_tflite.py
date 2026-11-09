"""Evaluate the shipped TFLite model on the held-out synthetic test set.

Usage (from the repo root):
    pip install ai-edge-litert scikit-learn pandas numpy
    python "stree detection/ml_model/evaluate_tflite.py" app/src/main/assets/stress_model.tflite app/src/main/assets/feature_stats.txt

Rebuilds the same 70/15/15 split as train_model.py (seed 42) and reports accuracy,
macro F1, the confusion matrix and single-sample latency on the current CPU.
"""
import sys, time, numpy as np
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from train_model import generate_realistic_data
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, f1_score, confusion_matrix
from ai_edge_litert.interpreter import Interpreter
model, stats = sys.argv[1], sys.argv[2]
names, mu, sd = [], [], []
for line in open(stats):
    k, _, v = line.strip().partition("=")
    if "," in v:
        names.append(k); m, s = v.split(","); mu.append(float(m)); sd.append(float(s))
df = generate_realistic_data(n_samples=3000)
X, y = df[names].to_numpy(np.float32), df["stress_level"].to_numpy()
_, Xt, _, yt = train_test_split(X, y, test_size=0.30, random_state=42, stratify=y)
_, Xte, _, yte = train_test_split(Xt, yt, test_size=0.50, random_state=42, stratify=yt)
Xn = ((Xte - np.array(mu)) / (np.array(sd) + 1e-8)).astype(np.float32)
it = Interpreter(model_path=model); it.allocate_tensors()
i, o = it.get_input_details()[0], it.get_output_details()[0]
print("input", i["dtype"], i["shape"], "output", o["dtype"])
preds, times = [], []
for row in Xn:
    it.set_tensor(i["index"], row[None]); t = time.perf_counter(); it.invoke(); times.append(time.perf_counter() - t)
    preds.append(int(np.argmax(it.get_tensor(o["index"]))))
preds = np.array(preds)
print(f"n={len(yte)} acc={accuracy_score(yte,preds):.4f} macroF1={f1_score(yte,preds,average='macro'):.4f}")
print(confusion_matrix(yte, preds))
t = np.array(times) * 1000
print(f"latency ms median={np.median(t):.4f} p95={np.percentile(t,95):.4f}")
