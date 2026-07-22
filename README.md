# TitanCore

Production-ready multiplayer browser game built phase by phase with enterprise Git workflow and CI quality gates.

## Structure

```text
backend
  auth
  player
  boss
  battle
  websocket
  reward
  ranking
  inventory
  common
  config
frontend
docker
nginx
docs
```

## Backend

Java 21 + Spring Boot 3 modular monolith.

Main backend packages live under:

```text
backend/src/main/java/com/game
```

```bash
cd backend
./gradlew bootRun
```

## Docker

Create a local `.env` from `.env.example`, then run:

```bash
docker compose up --build
```

## Docs

- `docs/PROJECT_RULES.md`
- `docs/GIT_WORKFLOW.md`
- `docs/phase-01-architecture.md`
- `docs/phase-01-project-skeleton-decisions.md`

## Workflow

TitanCore uses Git Flow:

```text
main
develop
feature/*
fix/*
refactor/*
perf/*
docs/*
hotfix/*
```

Do not commit directly to `main` or develop directly on `develop`. Every change starts from an approved issue and branch.
