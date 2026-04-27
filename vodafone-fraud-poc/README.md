# Vodafone Fraud Detection PoC

A live telecom fraud-detection proof of concept built with Apache Flink, Java, and a React dashboard.

The backend generates and processes simulated telecom events, detects fraud patterns, and streams alerts and events to the dashboard. The dashboard is designed for demos: it shows live event flow, detector output, run quality, expected-vs-actual alert counts, and an autopilot story mode.

## Features

- Apache Flink streaming pipeline for CDR and location events
- Fraud detectors for:
  - SIM clone / impossible location velocity
  - Sequential dialing
  - Static rule anomalies
  - Call-forwarding fan-out
- Scenario simulator with low, medium, and high intensity
- Near-miss scenarios to show detection boundaries
- Realistic background traffic mixed with fraud events
- Live dashboard with stream rows, charts, metrics, alert feed, and run quality
- Autopilot demo mode with narration markers and a guided timeline
- Demo reset endpoint to clear queues, counters, active runs, and dashboard state
- Docker Compose setup for one-command startup

## Architecture

```text
React dashboard
    |
    | HTTP + Server-Sent Events
    v
Java simulation server
    |
    | in-memory event queues
    v
Apache Flink job
    |
    v
Fraud detectors -> alert sink -> dashboard stream
```

## Requirements

For Docker:

- Docker Desktop or Docker Engine with Compose

For local development:

- Java 11 or newer
- Maven 3.9+
- Node.js 22+
- npm

## Run With Docker

From the project root:

```bash
docker compose up --build
```

Open the dashboard:

```text
http://localhost:5173
```

Backend API:

```text
http://localhost:8080
```

Stop the stack:

```bash
docker compose down
```

## Run Locally

Start the Java/Flink backend:

```bash
mvn clean compile
mvn exec:exec
```

In another terminal, start the dashboard:

```bash
cd dashboard
npm install
npm run dev -- --host 127.0.0.1
```

Open:

```text
http://127.0.0.1:5173
```

If the backend runs somewhere other than `localhost:8080`, set the dashboard API URL:

```bash
VITE_API_BASE_URL=http://localhost:8080 npm run dev -- --host 127.0.0.1
```

## Demo Flow

The easiest way to present the project is:

1. Open the dashboard.
2. Click `Reset` in the Autopilot Demo panel.
3. Click `Start Demo`.
4. Watch the Active Story Panel, timeline, highlighted stream rows, charts, and Run Quality table.

The demo walks through near misses first, then fraud scenarios, so viewers can see why alerts are raised and when the detectors correctly stay quiet.

## API Reference

### Status

```bash
curl http://localhost:8080/api/status
```

Returns server health, queue sizes, counters, active runs, completed runs, demo status, and available scenarios.

### Start a Simulation

```bash
curl -X POST http://localhost:8080/api/simulate \
  -H "Content-Type: application/json" \
  -d '{"scenario":"SEQUENTIAL_DIALING","intensity":"LOW"}'
```

Supported intensities:

- `LOW`
- `MEDIUM`
- `HIGH`

Supported scenarios:

- `SIM_CLONE`
- `SEQUENTIAL_DIALING`
- `STATICAL_RULE`
- `CALL_FORWARDING`
- `NEAR_MISS_SIM_CLONE`
- `NEAR_MISS_SEQUENTIAL`
- `NEAR_MISS_STATICAL_RULE`
- `NEAR_MISS_CALL_FORWARDING`

### Process One Manual Event

```bash
curl -X POST http://localhost:8080/api/process-event \
  -H "Content-Type: application/json" \
  -d '{"streamType":"CDR","msisdn":"905423184726","callee":"905337092418"}'
```

Location example:

```bash
curl -X POST http://localhost:8080/api/process-event \
  -H "Content-Type: application/json" \
  -d '{"streamType":"LOCATION","msisdn":"905423184726","location":"Istanbul/TR"}'
```

### Autopilot Demo

Start:

```bash
curl -X POST http://localhost:8080/api/demo
```

Stop:

```bash
curl -X DELETE http://localhost:8080/api/demo
```

Reset demo state:

```bash
curl -X POST http://localhost:8080/api/demo/reset
```

### Server-Sent Events

Alert stream:

```text
GET /api/alerts
```

Event stream:

```text
GET /api/events
```

## Project Structure

```text
.
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── src/main/java/com/vodafone/poc
│   ├── detector
│   ├── event
│   ├── generator
│   ├── server
│   └── sink
└── dashboard
    ├── Dockerfile
    ├── package.json
    └── src
```

## Development Checks

Backend compile:

```bash
mvn clean compile
```

Dashboard lint and build:

```bash
cd dashboard
npm run lint
npm run build
```

Docker build:

```bash
docker compose build
```

## Notes

- The app is intended as a demo and learning project, not a production fraud platform.
- Runtime state is in memory. Restarting the backend clears queues, runs, and alert history.
- The dashboard is compiled with `VITE_API_BASE_URL`; Docker Compose sets it to `http://localhost:8080` so it works from your browser.
