# Backend (Spring Boot)

REST API and WebSocket server for the telecom supervision platform.

## Tech Stack

- Java 17, Spring Boot 3, Spring Security (JWT), Spring WebSocket
- MySQL 8 (JPA/Hibernate)
- JSch (SSH remote management)

## Key Features

- JWT authentication with role-based access (ADMIN / VIEWER)
- Real-time metric collection via WebSocket
- SSH terminal to containerized network devices
- Alert rules and automated playbook execution
- Attack session tracking with MITRE ATT&CK mapping
- Email digest notifications

## Setup

```bash
# Copy example config
cp src/main/resources/application.properties.example src/main/resources/application.properties
# Edit with your MySQL credentials and JWT secret

# Build
./mvnw clean package

# Run
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`.

## API Endpoints

| Endpoint              | Description                    |
|-----------------------|--------------------------------|
| POST /api/auth/login  | JWT authentication             |
| GET /api/metrics      | Network metrics                |
| GET /api/devices      | Device status                  |
| GET /api/alerts       | Alert rules                    |
| WS /ws                | WebSocket for live updates     |
| POST /api/terminal    | SSH command execution          |
