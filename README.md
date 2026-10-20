## Live Demo

[Open AirRoute](https://airroute.onrender.com)

# AirRoute

AirRoute is a real-time air traffic routing and conflict simulation built with Java, Spring Boot, React, TypeScript, MapLibre, and WebSockets.

The application simulates nationwide U.S. air traffic, including aircraft movement, flight phases, weather avoidance, conflict prediction, and automatic conflict resolution.

## Features

- Simulates 60 aircraft operating between major U.S. airports
- Real-time aircraft position updates using WebSockets
- Aircraft flight phases:
  - Waiting
  - Climbing
  - En Route
  - Descending
  - Arrived
- Interactive nationwide map using MapLibre
- Aircraft search and selection
- Detailed aircraft information panel
- Historical flight trails
- Optional Show All Paths mode
- Aircraft filtering by flight phase
- Active conflict and predicted conflict filters
- Live airspace alerts panel
- Randomized severe weather hazards
- Geographic weather hazard zones
- Automatic weather avoidance
- Conflict detection
- Future conflict prediction
- Automatic conflict resolution using:
  - Speed adjustments
  - Altitude adjustments
  - Lateral rerouting
- Multiple simulation speeds:
  - 1x
  - 1.25x
  - 1.5x
  - 1.75x
  - 2x
  - 4x
- Start, pause, reset, and new scenario controls
- Live simulation statistics dashboard

## Tech Stack

### Backend

- Java
- Spring Boot
- Spring Web
- Spring WebSocket
- STOMP
- Maven

### Frontend

- React
- TypeScript
- Vite
- MapLibre GL JS
- STOMP.js

## Architecture

AirRoute uses a client-server architecture.

```text
React + TypeScript Frontend
        |
        | REST API
        | WebSockets / STOMP
        |
Spring Boot Backend
        |
        |-- Aircraft Simulation Engine
        |-- Weather Avoidance
        |-- Conflict Detection
        |-- Conflict Prediction
        |-- Conflict Resolution
        |-- Route Generation