#!/usr/bin/env python3

from flask import Flask, request, jsonify
from flask_cors import CORS
import requests as http_requests
import threading
import os
import json

from config.settings import (
    BACKEND_URL, MODEL_DIR, DATASET_DIR,
    WINDOW_SIZE, HIGH_CONFIDENCE_THRESHOLD,
    METRIC_FEATURES, INTERFACE_FEATURES, TCP_FEATURES,
    SECURITY_FEATURES, FEATURES,
)
from config import state
from training.trainer import train_from_csv, load_models
from prediction.predictor import predict_interface
from prediction.live_loop import live_prediction_loop

os.makedirs(MODEL_DIR, exist_ok=True)
os.makedirs(DATASET_DIR, exist_ok=True)

app = Flask(__name__)
CORS(app)



@app.route("/api/ai/train", methods=["POST"])
def api_train():

    try:
        body = request.get_json(silent=True) or {}

        server_csv = body.get("serverCsv",
                              f"{DATASET_DIR}/dataset_server.csv")
        router_csv = body.get("routerCsv",
                              f"{DATASET_DIR}/dataset_router.csv")

        results = {}

        # Train server model
        if os.path.exists(server_csv):
            print(f"Training SERVER model...")
            results["server"] = train_from_csv(server_csv, "server")
        else:
            results["server"] = {"error": f"File not found: {server_csv}"}

        # Train router model
        if os.path.exists(router_csv):
            print(f"Training ROUTER model...")
            results["router"] = train_from_csv(router_csv, "router")
        else:
            results["router"] = {"error": f"File not found: {router_csv}"}

        # 🔥 CLEAN BEFORE JSON
        return jsonify({
            "status": "success",
            "results": results
        })

    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/api/ai/predict", methods=["POST"])
def api_predict():

    try:
        ai_input = request.get_json()
        if not ai_input:
            return jsonify({"error": "No data provided"}), 400

        device_type = ai_input.get("deviceType", "unknown")
        if not state.xgb_models:
            return jsonify({
                "error": "Models not trained yet. Call /api/ai/train first"
            }), 400

        prediction = predict_interface(ai_input, device_type)
        if "error" in prediction:
            return jsonify(prediction), 400

        return jsonify(prediction)

    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/api/ai/predict/live", methods=["POST"])
def api_live_prediction():

    try:
        body = request.get_json() or {}
        action = body.get("action", "start")

        if action == "start":
            if not state.xgb_models:
                return jsonify({"error": "Models not trained yet"}), 400

            if state.live_prediction_active:
                return jsonify({"status": "already_running"})

            state.live_prediction_active = True
            state.live_prediction_thread = threading.Thread( # Thread = عملية تعمل في الخلفية بدون ما توقف البرنامج الرئيسي
                target=live_prediction_loop, daemon=True
            )
            state.live_prediction_thread.start()
            return jsonify({"status": "started"})

        elif action == "stop":
            state.live_prediction_active = False
            return jsonify({"status": "stopped"})

        else:
            return jsonify({"error": f"Unknown action: {action}"}), 400

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route("/api/ai/status", methods=["GET"])
def api_status():
    """Model status and configuration."""
    return jsonify({
        "modelsLoaded": {dtype: True for dtype in state.xgb_models},
        "totalModels": len(state.xgb_models),
        "livePredictionActive": state.live_prediction_active,
        "trainingStats": state.training_stats,
        "slidingWindowSize": WINDOW_SIZE,
        "highConfidenceThreshold": HIGH_CONFIDENCE_THRESHOLD,
        "features": {
            "metric": len(METRIC_FEATURES),
            "interface": len(INTERFACE_FEATURES),
            "tcp": len(TCP_FEATURES),
            "security": len(SECURITY_FEATURES),
            "total": len(FEATURES['server'])+len(FEATURES['router']),
        },
    })



@app.route("/api/ai/stats", methods=["GET"])
def api_dataset_stats():
    try:
        r = http_requests.get(
            f"{BACKEND_URL}/api/metrics/dataset/stats", timeout=10
        )
        return jsonify(r.json())
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/api/ai/config", methods=["PUT"])
def api_update_config():
    """
    Update AI configuration (sliding window, confidence threshold).

    Body: {
        "slidingWindowSize": 5,
        "highConfidenceThreshold": 0.90
    }

    Called from Dashboard Settings page.
    """
    try:
        import config.settings as settings_module

        body = request.get_json() or {}

        if "slidingWindowSize" in body:
            new_size = int(body["slidingWindowSize"])
            if 1 <= new_size <= 10:
                settings_module.WINDOW_SIZE = new_size
                # Clear existing windows so new size takes effect
                state.sliding_windows.clear()
                print(f"  ⚙️ Sliding window size updated to {new_size}")

        if "highConfidenceThreshold" in body:
            new_thresh = float(body["highConfidenceThreshold"])
            if 0.5 <= new_thresh <= 1.0:
                settings_module.HIGH_CONFIDENCE_THRESHOLD = new_thresh
                print(f"  ⚙️ High confidence threshold updated to {new_thresh}")

        return jsonify({
            "status": "updated",
            "slidingWindowSize": settings_module.WINDOW_SIZE,
            "highConfidenceThreshold": settings_module.HIGH_CONFIDENCE_THRESHOLD,
        })

    except Exception as e:
        return jsonify({"error": str(e)}), 500


if __name__ == "__main__":
    print(" AI Engine — Telecom Network Attack Detection")
    print("═" * 60)
    print(f"   Models dir:  {MODEL_DIR}/")
    print(f"   Dataset dir: {DATASET_DIR}/")
    print(f"   Features:    {len(FEATURES['server'])+len(FEATURES['router'])} total")
    print(f"     Metric:    {len(METRIC_FEATURES)}")
    print(f"     Interface: {len(INTERFACE_FEATURES)}")
    print(f"     TCP:       {len(TCP_FEATURES)}")
    print(f"     Security:  {len(SECURITY_FEATURES)}")
    print(f"   Window:      {WINDOW_SIZE} readings, majority vote")
    print(f"   Threshold:   {HIGH_CONFIDENCE_THRESHOLD} (immediate alert)")

    # Try to load pre-trained models
    load_models()
    if state.xgb_models:
        print(f"\n Loaded {len(state.xgb_models)} pre-trained models: "
              f"{list(state.xgb_models.keys())}")
    else:
        print("\n  No pre-trained models found.")
        print(f"   1. Place CSV files in {DATASET_DIR}/")
        print(f"      - dataset_server.csv")
        print(f"      - dataset_router.csv")
        print(f"   2. Call POST /api/ai/train")

    print("\n🚀 Starting Flask server on port 5000...\n")
    app.run(host="0.0.0.0", port=5000, debug=False)