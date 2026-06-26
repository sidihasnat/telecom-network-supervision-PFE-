# AI Module (Flask)

Real-time AI engine for network anomaly detection and attack classification.

## Tech Stack

- Flask (REST API)
- XGBoost (supervised classification)
- Isolation Forest (unsupervised anomaly detection)
- scikit-learn, pandas, numpy

## Architecture

```
ai-module/
├── app.py                    # Flask API entry point
├── config/
│   ├── settings.py           # Feature definitions and constants
│   └── state.py              # Runtime state management
├── prediction/
│   ├── predictor.py          # Model inference engine
│   ├── live_loop.py          # Real-time prediction loop
│   └── sliding_window.py     # Sliding window analysis
├── training/
│   └── trainer.py            # Model training pipeline
└── requirements.txt
```

## Models

Two model variants are trained:

- **Router model** (19 features): throughput, latency, packet loss, routing table size, etc.
- **Server model** (31 features): connections, TCP stats, security metrics, etc.

Each variant includes:
- `xgb_*.pkl` — XGBoost classifier
- `if_*.pkl` — Isolation Forest detector
- `le_*.pkl` — Label encoder
- `baseline_*.pkl` — Baseline statistics
- `features_*.pkl` — Feature list
- `stats_*.pkl` — Normalization statistics

## Setup

```bash
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# Train models (datasets must be in ../dataset/)
python -c "from training.trainer import *; train_all()"

# Run the API
python app.py
```

The AI engine starts on `http://localhost:5000`.
