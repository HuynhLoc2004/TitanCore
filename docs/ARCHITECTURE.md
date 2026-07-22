# Architecture

TitanCore uses a modular monolith.

## Why Modular Monolith

- Lower latency than microservices.
- Simpler deployment on one VPS.
- Easier debugging and transaction management.
- Strong internal module boundaries.
- Can evolve into separate services later if a module proves it needs independent scaling.

## Backend Modules

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

## Runtime Rule

The gameplay loop uses WebSocket and Redis. PostgreSQL and RabbitMQ are not allowed inside per-attack execution.

