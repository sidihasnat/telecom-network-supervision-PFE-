# Telecom Network Supervision Platform

> منصة إشراف شبكي ذكية تعتمد على الذكاء الاصطناعي لمراقبة وتأمين البنية التحتية للاتصالات

An AI-powered network supervision platform for monitoring, analyzing, and securing telecom infrastructure in real time. Built as a PFE (Projet de Fin d'Etudes) project using a **three-layer architecture**.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Couche Présentation                          │
│              (React Dashboard + Flutter Mobile)                 │
├─────────────────────────────────────────────────────────────────┤
│                    Couche Traitement                            │
│         Spring Boot API    │    Flask AI Engine                 │
│    (REST + WebSocket)      │  (XGBoost + Isolation Forest)     │
├─────────────────────────────────────────────────────────────────┤
│                    Couche Infrastructure                        │
│          ContainerLab Network Topology (14 nodes)              │
│    Routers │ Servers │ Clients │ Attacker │ Supervision Stack  │
└─────────────────────────────────────────────────────────────────┘
```

## Tech Stack

| Layer            | Technology                          | Purpose                              |
|------------------|-------------------------------------|--------------------------------------|
| Infrastructure   | ContainerLab, Docker                | Virtual network topology (14 nodes)  |
| Backend API      | Spring Boot 3, Java 17, MySQL       | REST API, WebSocket, SSH management  |
| AI Engine        | Flask, XGBoost, Isolation Forest    | Real-time anomaly detection & classification |
| Frontend         | React, Tailwind CSS                 | Web dashboard with live monitoring   |
| Mobile           | Flutter                             | Mobile supervision app               |
| Database         | MySQL 8                             | Metrics, alerts, and audit storage   |

## Project Structure

```
├── infrastructure/      # ContainerLab topology, Dockerfiles, init scripts
├── backend/             # Spring Boot REST API (Java/Maven)
├── ai-module/           # Flask AI engine (XGBoost + Isolation Forest)
├── frontend/            # React web dashboard
├── mobile/              # Flutter mobile app
├── dataset/             # Training datasets (router & server metrics)
├── agents/              # Network agents (metrics collector, attack simulator)
└── docs/                # Documentation and screenshots
```

## Getting Started

### Prerequisites

- Docker & [ContainerLab](https://containerlab.dev/) installed
- Java 17+ & Maven
- Python 3.10+ & pip
- Node.js 18+ & npm

### 1. Infrastructure (ContainerLab)

```bash
cd infrastructure/

# Build all Docker images
sudo bash build-supervision.sh

# Deploy the network topology
sudo clab deploy -t topology.yml
```

### 2. Backend (Spring Boot)

```bash
cd backend/

# Copy and configure the properties file
cp src/main/resources/application.properties.example src/main/resources/application.properties
# Edit application.properties with your MySQL credentials

# Build and run
./mvnw spring-boot:run
```

### 3. AI Module (Flask)

```bash
cd ai-module/

# Create virtual environment
python3 -m venv venv
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Run the AI engine
python app.py
```

### 4. Frontend (React)

```bash
cd frontend/

# Install dependencies
npm install

# Start development server
npm start
```

### 5. Mobile (Flutter)

```bash
cd mobile/

# Get dependencies
flutter pub get

# Run the app
flutter run
```

## Network Topology

The ContainerLab topology consists of 14 nodes across 4 network zones:

- **WAN Zone** (10.0.0.0/24): Edge router, external attacker
- **DMZ Zone** (10.0.1.0/24): Web server, DNS server
- **LAN Zone** (10.0.2.0/24): FTP server, DB server, PCs
- **Management Zone** (10.0.4.0/24): Supervision stack (web, app, db, ai)

## AI Models

- **XGBoost**: Supervised classification of network anomalies
- **Isolation Forest**: Unsupervised anomaly detection
- Two model variants: `router` (19 features) and `server` (31 features)
- Real-time prediction via sliding window analysis

## Screenshots

> Screenshots will be added in `docs/screenshots/`

## Security Notice

- Copy `.example` config files and fill in your own credentials before running
- Never commit `application.properties` files containing real credentials
- Default admin credentials should be changed immediately after first login

## License

This project was developed as a PFE (Projet de Fin d'Etudes).
