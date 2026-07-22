# Development Setup

## Prerequisites

- Java 21
- Docker
- Docker Compose
- Node.js 20 or newer

## Backend

```bash
cd backend
./gradlew bootRun
```

## Frontend

```bash
cd frontend
npm install
npm run dev
```

## Docker

```bash
cp .env.example .env
docker compose up --build
```

