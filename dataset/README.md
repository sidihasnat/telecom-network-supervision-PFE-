# Dataset

Training datasets for the AI anomaly detection models.

## Files

| File                 | Description                                  |
|----------------------|----------------------------------------------|
| dataset_router.csv   | Router metrics with labeled anomalies (19 features) |
| dataset_server.csv   | Server metrics with labeled anomalies (31 features) |

## Features

Data is collected from the ContainerLab network using the `agents/metrics.py` collector. Each row represents a snapshot of network metrics at a given time.

Labels: `normal`, `syn_flood`, `port_scan`, `brute_force`, `ddos`, etc.
