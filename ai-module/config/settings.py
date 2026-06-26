"""
Configuration and feature definitions.
All constants used across the AI Engine.
"""

# ══════════════════════════════════════════════════════════════
# GENERAL CONFIG
# ══════════════════════════════════════════════════════════════
BACKEND_URL = "http://localhost:8080"
MODEL_DIR = "models"
DATASET_DIR = "datasets"

# ══════════════════════════════════════════════════════════════
# SLIDING WINDOW CONFIG
# ══════════════════════════════════════════════════════════════
WINDOW_SIZE = 3
HIGH_CONFIDENCE_THRESHOLD = 0.95

# ══════════════════════════════════════════════════════════════
# FEATURES FOR LIVE_LOOP — builds AiInput from raw metrics
# ══════════════════════════════════════════════════════════════

METRIC_FEATURES = [
    #"cpuUsage",
    #"memoryUsage",
    #"connections",
]

INTERFACE_FEATURES = [
    "throughputOut",
    "throughputIn",
    "packetsPerSecSent",
    "packetsPerSecRecv",
    "errorRate",
    "dropRate",
    "latency",
    "packetLoss",
    "jitter",
    "bytesPerPacketOut",
    "bytesPerPacketIn",
    "bandwidthUtilOut",
    "bandwidthUtilIn",
]

TCP_FEATURES = [
    "currEstab",
    "passiveOpensRate",
    "activeOpensRate",
    "attemptFailsRate",
    "outRstsRate",
    "inSegsRate",
    "outSegsRate",
    "retransRate",
    "icmpInRate",
    "inOutSegRatio",
    "udpInRate",
    "udpOutRate",
    "udpNoPortRate",
    "udpInOutRatio",
    "routingTableSize",
]

SECURITY_FEATURES = [
    "halfOpenConnections",
    "uniqueSourceIPs",
    "topAttackerRepeat",
    "uniqueDestinationPorts",
    "sensitivePortsHit",
    "timeWaitConnections",
    "avgConnectionDuration",
    "longConnectionRatio",
]
# ══════════════════════════════════════════════════════════════
# FEATURES PER MODEL — match dataset CSVs exactly
# ══════════════════════════════════════════════════════════════

SERVER_FEATURES = [
    "throughputOut",
    "throughputIn",
    "packetsPerSecSent",
    "packetsPerSecRecv",
    "latency",
    "jitter",
    "bytesPerPacketOut",
    "bytesPerPacketIn",
    "bandwidthUtilOut",
    "bandwidthUtilIn",
    "currEstab",
    "passiveOpensRate",
    "activeOpensRate",
    "attemptFailsRate",
    "outRstsRate",
    "inSegsRate",
    "outSegsRate",
    "retransRate",
    "icmpInRate",
    "inOutSegRatio",
    "udpInRate",
    "udpOutRate",
    "udpNoPortRate",
    "udpInOutRatio",
    "halfOpenConnections",
    "uniqueSourceIPs",
    "topAttackerRepeat",
    "uniqueDestinationPorts",
    "sensitivePortsHit",
    "avgConnectionDuration",
    "longConnectionRatio",
]

ROUTER_FEATURES = [
    "throughputOut",
    "throughputIn",
    "packetsPerSecSent",
    "packetsPerSecRecv",
    "latency",
    "packetLoss",
    "jitter",
    "bytesPerPacketOut",
    "bytesPerPacketIn",
    "bandwidthUtilOut",
    "bandwidthUtilIn",
    "outRstsRate",
    "inSegsRate",
    "outSegsRate",
    "icmpInRate",
    "routingTableSize",
    "topAttackerRepeat",
    "uniqueDestinationPorts",
    "timeWaitConnections",
]

FEATURES = {
    "server": SERVER_FEATURES,
    "router": ROUTER_FEATURES,
}