# Phase 1 - Project Skeleton Decisions

## Goal

Build the complete production-oriented project skeleton without business logic.

This phase creates the foundation for a modular monolith multiplayer game using Java 21, Spring Boot 3, Docker, Spring profiles, Swagger, structured logging, environment-based configuration, and unified exception handling.

## Build Tool

Chosen tool: Gradle.

Why:

- Faster incremental builds than Maven for larger projects.
- Concise dependency management.
- Good Spring Boot support.
- Easier future migration to a multi-project build if the modular monolith grows.

Alternative:

- Maven is stable and familiar, but more verbose. It is still a good choice, but Gradle fits this project better because we will iterate quickly across backend, Docker, and deployment code.

## Backend Shape

Chosen architecture: one Spring Boot application using internal modules.

Why:

- Keeps deployment simple on a single VPS.
- Avoids network overhead between modules.
- Keeps transactions and debugging simpler.
- Still allows strong package boundaries.

Alternative:

- Microservices are intentionally rejected for Phase 1 because they increase infrastructure cost, latency, and operational complexity.

## Module Boundaries

Each domain module is represented by a package under `com.game`.

Modules:

- `auth`
- `player`
- `battle`
- `boss`
- `inventory`
- `reward`
- `ranking`
- `websocket`
- `payment`
- `ai`
- `notification`
- `common`
- `config`

Business logic is not implemented yet. Each module contains package placeholders through `package-info.java` files so boundaries are explicit from day one.

## Configuration

Spring profile files:

- `application.yml`: shared defaults.
- `application-local.yml`: local machine development.
- `application-dev.yml`: shared development environment.
- `application-prod.yml`: production-oriented settings.

Environment variables are referenced from YAML. Secrets are never hardcoded.

## .env Strategy

`.env.example` documents required variables. Real `.env` files are ignored by Git.

Spring Boot does not load `.env` by itself. Docker Compose uses `.env`, and local developers can export variables or use IDE run configuration environment variables.

## API Documentation

Swagger/OpenAPI is configured through `springdoc-openapi`.

Swagger UI is enabled in local/dev and disabled in prod by default using environment-driven config.

## Exception Handling

The skeleton includes a global exception handler returning a unified response shape:

```json
{
  "timestamp": "",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/example"
}
```

Stack traces and raw exception messages must not be exposed to clients.

## Logging

Logging is configured with Logback.

The skeleton uses a consistent console pattern and keeps package-level log configuration in YAML. Sensitive values must be masked before logging in future phases.

## Docker

Docker setup includes:

- Backend Dockerfile.
- Docker Compose with backend, PostgreSQL, Redis, RabbitMQ, and Nginx.
- Nginx reverse proxy config.

The Compose stack is infrastructure-ready but business endpoints remain minimal.

## First Endpoint

Only one technical endpoint is included:

- `GET /api/health`

It verifies that the app boots and HTTP routing works. It is not business logic.
