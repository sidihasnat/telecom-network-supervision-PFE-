# Agents

Network agents deployed inside ContainerLab containers for metric collection, traffic generation, and attack simulation.

## Files

| File                    | Description                                          |
|-------------------------|------------------------------------------------------|
| metrics.py              | SNMP/SSH metric collector (runs on each network node)|
| attack_simulator.py     | Automated attack simulator (SYN flood, port scan, brute force) |
| background_traffic.sh   | Normal background traffic generator                  |
| wordlist.txt            | Password list for brute force simulation             |

## Usage

These scripts are automatically deployed into containers via the init scripts in `infrastructure/init-scripts/`. They are bind-mounted from `infrastructure/` into each container at `/scripts/`.
