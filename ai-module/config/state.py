"""
Shared global state across all modules.
Models, encoders, baselines, and live prediction state.
"""

# Models per device_type: {"server": model, "router": model}
xgb_models = {}
if_models = {}
label_encoders = {}
feature_lists = {}
training_stats = {}

# Normal baselines per device_type — for topFeatures comparison
# {"server": {"halfOpenConnections": 2.1, ...}}
normal_baselines = {}

# Live prediction state
live_prediction_active = False
live_prediction_thread = None

# Sliding windows per interface: {"web-server::eth1": deque([pred1, pred2, pred3])}
sliding_windows = {}
