"""
Model training from CSV files and model persistence.
Trains XGBoost (classification) + Isolation Forest (anomaly detection).
"""

import pandas as pd
import xgboost as xgb
from sklearn.ensemble import IsolationForest
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, accuracy_score, f1_score
from sklearn.preprocessing import LabelEncoder
import joblib
import os
from datetime import datetime

from config.settings import FEATURES, MODEL_DIR
from config import state


def train_from_csv(csv_path, device_type):
    """
    Train XGBoost + Isolation Forest from a CSV file.

    CSV must have:
      - attackLabel column
      - feature columns matching the device type

    Args:
        csv_path: path to dataset_server.csv or dataset_router.csv
        device_type: "server" or "router"

    Returns:
        dict with training results
    """

    print(f"\n📊 Loading {csv_path}...")

    if device_type not in FEATURES:
        return {"error": f"Unknown device type: {device_type}"}

    feature_list = FEATURES[device_type]
    df = pd.read_csv(csv_path) #DataFrame

    if "attackLabel" not in df.columns:
        return {"error": "CSV must have 'attackLabel' column"}

    # ── Add missing feature columns as 0 ──────────────────────
    missing_cols = [c for c in feature_list if c not in df.columns]
    if missing_cols:
        print(f"  ⚠️ Missing columns (will be 0): {missing_cols}")
        for col in missing_cols:
            df[col] = 0

    X = df[feature_list].fillna(0).values
    y = df["attackLabel"].values

    print(f"   Rows: {len(df)}")
    print(f"   Features: {len(feature_list)}")
    print(f"   Labels: {dict(pd.Series(y).value_counts())}")

    if len(df) < 20:
        return {"error": f"Not enough data: {len(df)} rows (need at least 20)"}

    # ── Compute normal baseline ───────────────────────────────
    normal_mask = y == "normal"

    if normal_mask.sum() > 0:
        normal_df = df[normal_mask][feature_list].fillna(0)
        state.normal_baselines[device_type] = normal_df.mean().to_dict()
        print(f"   Normal baseline computed from {normal_mask.sum()} rows")

    # ── Label Encoding ────────────────────────────────────────
    le = LabelEncoder()
    y_encoded = le.fit_transform(y)


    X_train, X_test, y_train, y_test = train_test_split(
        X,
        y_encoded,
        test_size=0.2, #20% test, 80% train
        random_state=42, #(graine aléatoire).Permet d’avoir toujours le même split
        stratify=y_encoded,  #garder la même proportion de classes dans train et test
    )

    # ── XGBoost Training ──────────────────────────────────────
    num_classes = len(le.classes_)

    xgb_model = xgb.XGBClassifier(
        n_estimators=100, #nombre d’arbres de décision plus = modèle plus puissant mais plus lent
        max_depth=6,
        learning_rate=0.1,
        objective="multi:softprob" if num_classes > 2 else "binary:logistic",
        num_class=num_classes if num_classes > 2 else None,
        eval_metric="mlogloss" if num_classes > 2 else "logloss",
        use_label_encoder=False,
        random_state=42,
        verbosity=0,
    )

    xgb_model.fit(X_train, y_train)

    # ── Evaluation ────────────────────────────────────────────
    y_pred = xgb_model.predict(X_test)

    accuracy = accuracy_score(y_test, y_pred)
    f1 = f1_score(y_test, y_pred, average="weighted")

    report = classification_report(
        y_test,
        y_pred,
        target_names=le.classes_,
        output_dict=True,
    )

    print(f"   ✅ Accuracy: {accuracy:.4f}, F1: {f1:.4f}")
    print(f"   Classes: {list(le.classes_)}")

    # ── Isolation Forest Training ─────────────────────────────
    X_normal = df[normal_mask][feature_list].fillna(0).values

    if_model = IsolationForest(
        n_estimators=100,
        contamination=0.05,
        random_state=42,
    )

    if len(X_normal) >= 10:
        if_model.fit(X_normal)
        print(f"   Isolation Forest trained on {len(X_normal)} normal rows")
    else:
        if_model.fit(X_train)
        print("   ⚠️ Not enough normal data, IF trained on all training data")

    # ── Save models to memory ─────────────────────────────────
    state.xgb_models[device_type] = xgb_model
    state.if_models[device_type] = if_model
    state.label_encoders[device_type] = le
    state.feature_lists[device_type] = feature_list

    # ── Save models to disk ───────────────────────────────────
    joblib.dump(xgb_model, f"{MODEL_DIR}/xgb_{device_type}.pkl")
    joblib.dump(if_model, f"{MODEL_DIR}/if_{device_type}.pkl")
    joblib.dump(le, f"{MODEL_DIR}/le_{device_type}.pkl")
    joblib.dump(feature_list, f"{MODEL_DIR}/features_{device_type}.pkl")

    if device_type in state.normal_baselines:
        joblib.dump(
            state.normal_baselines[device_type],
            f"{MODEL_DIR}/baseline_{device_type}.pkl",
        )

    # ── Save training stats to disk ───────────────────────────
    stats_to_save = {
        "accuracy": round(accuracy, 4),
        "f1_score": round(f1, 4),
        "classes": list(le.classes_),
        "samples": len(df),
        "train_samples": len(X_train),
        "test_samples": len(X_test),
        "trained_at": datetime.now().isoformat(),
    }

    joblib.dump(stats_to_save, f"{MODEL_DIR}/stats_{device_type}.pkl")

    # ── Feature Importance ────────────────────────────────────
    importance = dict(zip(feature_list, xgb_model.feature_importances_))
    top_features = dict(
        sorted(importance.items(), key=lambda x: x[1], reverse=True)[:10]
    )

    # تحويل classification_report من float32 إلى float عادي
    clean_report = {}
    for k, v in report.items():
        if isinstance(v, dict):
            clean_report[k] = {kk: float(vv) for kk, vv in v.items()}
        else:
            clean_report[k] = float(v)

    result = {
        "accuracy": float(round(accuracy, 4)),
        "f1_score": float(round(f1, 4)),
        "train_samples": int(len(X_train)),
        "test_samples": int(len(X_test)),
        "classes": list(le.classes_),
        "classification_report": clean_report,
        "top_features": {k: float(v) for k, v in top_features.items()},
    }

    state.training_stats[device_type] = {
        "trained_at": datetime.now().isoformat(),
        "samples": len(df),
        **result,
    }

    return result


def load_models():
    """Load pre-trained models from disk."""

    for dtype in ["server", "router"]:
        xgb_path = f"{MODEL_DIR}/xgb_{dtype}.pkl"
        if_path = f"{MODEL_DIR}/if_{dtype}.pkl"
        le_path = f"{MODEL_DIR}/le_{dtype}.pkl"
        baseline_path = f"{MODEL_DIR}/baseline_{dtype}.pkl"
        features_path = f"{MODEL_DIR}/features_{dtype}.pkl"
        stats_path = f"{MODEL_DIR}/stats_{dtype}.pkl"

        if os.path.exists(xgb_path):
            state.xgb_models[dtype] = joblib.load(xgb_path)
            state.if_models[dtype] = joblib.load(if_path)
            state.label_encoders[dtype] = joblib.load(le_path)

            if os.path.exists(features_path):
                state.feature_lists[dtype] = joblib.load(features_path)
            else:
                state.feature_lists[dtype] = FEATURES.get(dtype, [])

            if os.path.exists(baseline_path):
                state.normal_baselines[dtype] = joblib.load(baseline_path)

            if os.path.exists(stats_path):
                state.training_stats[dtype] = joblib.load(stats_path)

            print(f"  ✅ Loaded models for {dtype}")