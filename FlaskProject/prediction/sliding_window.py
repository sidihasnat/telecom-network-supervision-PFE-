"""
Sliding window for prediction smoothing.
Reduces false positives by requiring majority vote across 3 readings.
Exception: high confidence (>95%) triggers immediate alert.
"""

from collections import deque

from config.settings import WINDOW_SIZE, HIGH_CONFIDENCE_THRESHOLD
from config import state


def apply_sliding_window(interface_key, prediction):
    """
    Sliding window: 3 readings, majority vote.
    Exception: confidence > 95% → immediate alert.

    Args:
        interface_key: "web-server::eth1" (device + interface)
        prediction: dict from predict_interface()
    """
    if interface_key not in state.sliding_windows:
        state.sliding_windows[interface_key] = deque(maxlen=WINDOW_SIZE)

    window = state.sliding_windows[interface_key]
    window.append(prediction)

    # Exception: high confidence → immediate alert
    if prediction["confidence"] >= HIGH_CONFIDENCE_THRESHOLD:
        return {
            **prediction,
            "windowDecision": prediction["predictedAttack"],
            "windowMethod": "high_confidence_immediate",
        }

    # Not enough data yet
    if len(window) < 2:
        return {
            **prediction,
            "windowDecision": "normal",
            "windowMethod": "insufficient_data",
        }

    # Majority vote
    votes = {}
    for pred in window:
        label = pred["predictedAttack"]
        votes[label] = votes.get(label, 0) + 1

    majority_label = max(votes, key=votes.get)
    majority_count = votes[majority_label]
    if majority_count >= 2:
        return {
            **prediction,
            "windowDecision": majority_label,
            "windowMethod": f"majority_{majority_count}_of_{len(window)}",
            "windowVotes": votes,
        }
    else:
        return {
            **prediction,
            "windowDecision": "normal",
            "windowMethod": f"no_majority_{len(window)}_of_{len(window)}",
            "windowVotes": votes,
        }
