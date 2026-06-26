"""
Per-interface prediction using XGBoost + Isolation Forest.
Includes topFeatures computation (deviation from normal baseline).
"""

import numpy as np
import json

from config.settings import FEATURES
from config import state


def predict_interface(ai_input, device_type):
    """
    Predict attack type for a single interface.

    Args:
        ai_input: dict with ALL_FEATURES (flat AiInput from Spring Boot)
        device_type: "server" or "router"

    Returns:
        dict with prediction results + topFeatures
    """
    if device_type not in state.xgb_models:
        return {"error": f"No model for device type: {device_type}"}

    xgb_model = state.xgb_models[device_type]
    if_model = state.if_models[device_type]
    le = state.label_encoders[device_type]
    feature_list = state.feature_lists.get(device_type, FEATURES.get(device_type, []))

    # Build feature vector
    X = np.array([[ai_input.get(f, 0) or 0 for f in feature_list]])

    # ── XGBoost prediction ────────────────────────────────────
    proba = xgb_model.predict_proba(X)[0]  #predict_proba(X) -> [[0.1, 0.8, 0.1]]
    pred_idx = np.argmax(proba)
    pred_label = le.inverse_transform([pred_idx])[0] # array(['ddos'], dtype=object) [0] => ['ddos']
    confidence = float(proba[pred_idx])

    # ── Isolation Forest anomaly score ────────────────────────
    anomaly_score = float(if_model.decision_function(X)[0])

    # ── All probabilities ─────────────────────────────────────
    all_proba = {le.inverse_transform([i])[0]: round(float(p), 4)
                 for i, p in enumerate(proba)}

    # ── Top Features (what triggered the prediction) ──────────
    top_features = compute_top_features(ai_input, device_type, feature_list)

    return {
        "interfaceMetricId": ai_input.get("interfaceMetricId"),
        "predictedAttack": pred_label,
        "confidence": round(confidence, 4),
        "anomalyScore": round(anomaly_score, 4),
        "faultProbability": None,
        "topFeatures": json.dumps(top_features),
        "allProbabilities": all_proba,
    }


def compute_top_features(ai_input, device_type, feature_list):
    """
    Find features that deviate most from normal baseline.

    Returns list of top 5 features:
    [{"name": "halfOpenConnections", "value": 890, "normalAvg": 2.1}, ...]
    """
    if device_type not in state.normal_baselines:
        return []

    baseline = state.normal_baselines[device_type]
    deviations = []

    for feature in feature_list:
        current_val = ai_input.get(feature, 0) or 0
        normal_avg = baseline.get(feature, 0) or 0

        # Calculate deviation (how far from normal)
        if normal_avg != 0:
            deviation = abs(current_val - normal_avg) / abs(normal_avg)
        elif current_val != 0:
            deviation = abs(current_val)
        else:
            deviation = 0

        if deviation > 0.5:  # Only features that changed significantly
            deviations.append({
                "name": feature,
                "value": round(current_val, 2),
                "normalAvg": round(normal_avg, 2),
                "deviation": round(deviation, 2),
            })

    # Sort by deviation and return top 5
    deviations.sort(key=lambda x: x["deviation"], reverse=True)
    return deviations[:5]
