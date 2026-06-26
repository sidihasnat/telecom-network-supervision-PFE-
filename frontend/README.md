# Frontend (React)

Web dashboard for the telecom network supervision platform.

## Tech Stack

- React 18, Tailwind CSS
- WebSocket for real-time updates
- REST API integration

## Features

- Live network topology visualization
- Real-time metric monitoring (throughput, latency, packet loss)
- Security dashboard with attack detection alerts
- MITRE ATT&CK mapping for detected threats
- SSH terminal to network devices
- Alert configuration and management
- User authentication (JWT)

## Setup

```bash
npm install
npm start
```

The dashboard starts on `http://localhost:3000`.

## Pages

| Page       | Description                              |
|------------|------------------------------------------|
| Home       | Dashboard overview with topology map     |
| Monitoring | Real-time metrics and graphs             |
| Security   | Attack detection and MITRE mapping       |
| Terminal   | SSH terminal to network devices          |
| Settings   | Alert rules, email config, user management |
