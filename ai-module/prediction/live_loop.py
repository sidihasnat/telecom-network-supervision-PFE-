"""
Live prediction loop.
Runs in a background thread, pulls metrics every 10 seconds,
predicts per interface, and sends results back to Spring Boot.
"""

import requests as http_requests
import time
from datetime import datetime, timedelta

from config.settings import (
    BACKEND_URL, METRIC_FEATURES, INTERFACE_FEATURES,
    TCP_FEATURES, SECURITY_FEATURES
)
from config import state
from prediction.predictor import predict_interface
from prediction.sliding_window import apply_sliding_window


def live_prediction_loop():
    """
    Background thread that:
    1. Pulls latest metrics from Spring Boot
    2. Builds AiInput per interface
    3. Predicts using XGBoost + Isolation Forest
    4. Applies sliding window
    5. Sends results back to Spring Boot
    """
    last_processed_ids = set()

    print("🔴 Live prediction started")

    while state.live_prediction_active:
        try:
            # Pull latest metrics from Backend
            r = http_requests.get(
                f"{BACKEND_URL}/api/metrics/latest", timeout=10
            )
            metrics = r.json()

            for metric in metrics:
                # Skip old metrics (prevent re-analyzing old data)
                timestamp = metric.get("timestamp")
                if timestamp:
                    try:
                        metric_time = datetime.fromisoformat(timestamp)
                        if datetime.now() - metric_time > timedelta(seconds=30):
                            continue
                    except:
                        pass

                device_type = metric.get("deviceType", "unknown")
                device_name = metric.get("deviceName", "unknown")

                # Skip if no model for this device type
                if device_type not in state.xgb_models:
                    continue

                interfaces = metric.get("interfaces") or []
                for iface in interfaces:
                    iface_id = iface.get("id")
                    if not iface_id or iface_id in last_processed_ids:
                        continue

                    # Build flat AiInput
                    ai_input = build_ai_input_from_metric(metric, iface)

                    # Predict
                    prediction = predict_interface(ai_input, device_type)
                    if "error" in prediction:
                        continue

                    # Sliding window per interface
                    iface_name = iface.get("interfaceName", "unknown")
                    interface_key = f"{device_name}::{iface_name}"
                    final = apply_sliding_window(interface_key, prediction)

                    # Send result to Backend — only confirmed attacks
                    if final["windowDecision"] != "normal":
                        method = final.get("windowMethod", "")
                        if "insufficient_data" not in method:
                            try:
                                http_requests.post(
                                    f"{BACKEND_URL}/api/metrics/prediction",
                                    json={
                                        "interfaceMetricId": iface_id,
                                        "predictedAttack": final["windowDecision"],
                                        "confidence": final["confidence"],
                                        "anomalyScore": final.get("anomalyScore"),
                                        "faultProbability": final.get("faultProbability"),
                                        "topFeatures": final.get("topFeatures"),
                                    },
                                    timeout=5,
                                )
                            except Exception as e:
                                print(f"  ⚠️ Could not save prediction: {e}")

                    last_processed_ids.add(iface_id)

                    # Log attacks only
                    decision = final["windowDecision"]
                    conf = final["confidence"]
                    method = final.get("windowMethod", "")

                    if decision != "normal":
                        print(f"  🚨 {interface_key}: {decision} "
                              f"(conf={conf:.2f}, {method})")

            # Keep only last 500 IDs to prevent memory leak
            if len(last_processed_ids) > 500:
                last_processed_ids = set(list(last_processed_ids)[-250:])

        except Exception as e:
            print(f"  ⚠️ Live prediction error: {e}")

        time.sleep(10)

    print("⬛ Live prediction stopped")


def build_ai_input_from_metric(metric, iface):
    """
    Build a flat AiInput dict from MetricData + InterfaceMetric.
    Same logic as MetricService.buildAiInput() in Java.
    """
    tcp = metric.get("tcpStats") or {}
    sec = metric.get("securityMetric") or {}

    ai_input = {"interfaceMetricId": iface.get("id")}

    # From MetricData
    for f in METRIC_FEATURES:
        ai_input[f] = metric.get(f, 0)

    # From InterfaceMetric
    for f in INTERFACE_FEATURES:
        ai_input[f] = iface.get(f, 0)

    # From TcpStats
    for f in TCP_FEATURES:
        ai_input[f] = tcp.get(f, 0)

    # From SecurityMetric
    for f in SECURITY_FEATURES:
        ai_input[f] = sec.get(f, 0)

    return ai_input