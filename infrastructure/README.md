# Infrastructure

ContainerLab network topology and container configuration for the telecom supervision platform.

## Structure

```
infrastructure/
├── topology.yml              # Main ContainerLab topology (14 nodes)
├── dockerfiles/              # Docker images for all network nodes
│   ├── Dockerfile.router     # Edge & core routers (Alpine + iptables)
│   ├── Dockerfile.web        # Web server (Apache/Nginx)
│   ├── Dockerfile.dns        # DNS server
│   ├── Dockerfile.db         # Database server (MariaDB)
│   ├── Dockerfile.client     # LAN clients (PC)
│   ├── Dockerfile.ftp        # FTP server
│   ├── Dockerfile.attacker   # WAN attacker node
│   ├── Dockerfile.php        # PHP support
│   └── Dockerfile.supervision-*  # Supervision stack (web, app, db, ai)
├── init-scripts/             # Container initialization scripts
├── service-scripts/          # Service startup scripts (DNS, DB, web)
├── nginx.conf                # Nginx reverse proxy config
├── build-supervision.sh      # Build all Docker images
└── setup-host-routes.sh      # Host routing setup
```

## Usage

```bash
# Build all container images
sudo bash build-supervision.sh

# Deploy the topology
sudo clab deploy -t topology.yml

# Destroy the topology
sudo clab destroy -t topology.yml

# Setup host routes (for accessing containers from host)
sudo bash setup-host-routes.sh
```

## Network Zones

| Zone       | Subnet        | Nodes                              |
|------------|---------------|------------------------------------|
| WAN        | 10.0.0.0/24   | edge-router, attacker              |
| DMZ        | 10.0.1.0/24   | web-server, dns-server             |
| LAN        | 10.0.2.0/24   | ftp-server, db-server, pc1, pc2    |
| Management | 10.0.4.0/24   | supervision-web/app/db/ai          |
