# Phase 1 - Architecture

## Game Direction

The first playable version is a real-time multiplayer boss raid browser game.

Players connect through WebSocket, join a boss room, attack the active boss, receive live damage updates, and earn rewards after the boss is defeated. This direction fits the required modules well and keeps the first production slice focused enough to optimize correctly.

## Architectural Choice

We will build a modular monolith.

This gives us strong module boundaries without the operational cost of microservices. For a single VPS target with 100-200 concurrent players, a modular monolith is faster to deploy, easier to debug, cheaper to run, and more predictable under load.

Alternatives considered:

- Microservices: unnecessary network overhead, more deployment complexity, harder local development, and higher VPS cost.
- Single layered monolith: fast to start, but module ownership becomes unclear as auth, battle, inventory, rewards, AI, and payment grow.
- Modular monolith: best balance of performance, maintainability, and future scalability.

## Runtime Topology

```text
Browser
  React + Phaser
  REST + WebSocket
      |
      v
Nginx
  HTTPS termination
  Static frontend hosting
  Reverse proxy
      |
      v
Spring Boot Modular Monolith
  REST API
  WebSocket Gateway
  Game Engine
  Auth/Security
  Domain Modules
      |
      +--> Redis
      |     Boss HP
      |     Damage
      |     Cooldowns
      |     Sessions
      |     Online players
      |     Leaderboards
      |
      +--> PostgreSQL
      |     Durable player data
      |     Inventory
      |     Payments
      |     Rewards history
      |     AI generated content
      |
      +--> RabbitMQ
            Async rewards
            Notifications
            AI generation jobs
            Image generation jobs
            Ranking persistence
```

## Core Rule

The gameplay loop must not touch PostgreSQL or RabbitMQ.

```text
Player Attack
  -> WebSocket
  -> Server-side validation
  -> Game Engine
  -> Redis atomic update
  -> Room broadcast
```

PostgreSQL is used before and after gameplay, not during each attack. RabbitMQ is used after gameplay events for asynchronous work.

## Backend Modules

Each module follows Clean Architecture with controller, service, repository, entity, DTO, mapper, validator, and specification when needed.

Initial modules:

- `auth`: login, OAuth2, JWT rotation, refresh tokens, Redis blacklist.
- `player`: profile, stats, cosmetics, progression.
- `battle`: attack validation, damage calculation, battle sessions.
- `boss`: boss state, boss metadata, active boss rotation.
- `reward`: reward calculation and asynchronous claiming.
- `inventory`: owned items, currencies, cosmetics.
- `ranking`: Redis leaderboard and PostgreSQL persistence.
- `match`: room joining, matchmaking, session recovery.
- `websocket`: connection lifecycle, heartbeat, broadcast, reconnect.
- `ai`: JSON-only content generation, cache, validation, persistence.
- `shop`: cosmetic-only product catalog.
- `payment`: PayOS integration, webhook validation, purchase records.
- `notification`: async user notifications.
- `common`: shared error, result, constants, utility types.
- `config`: security, Redis, PostgreSQL, RabbitMQ, OpenAPI, CORS.

## Backend Package Shape

```text
server/src/main/java/com/game
  auth
    controller
    service
    repository
    entity
    dto
    mapper
    validator
  battle
    engine
    controller
    service
    dto
    validator
  boss
  player
  inventory
  reward
  ranking
  match
  websocket
  ai
  shop
  payment
  notification
  common
  config
```

## Frontend Architecture

The frontend is a React application using Phaser for rendering the game scene.

```text
client/src
  app
  api
  auth
  game
    phaser
    scenes
    systems
    sprites
  features
    battle
    boss
    inventory
    ranking
    shop
  websocket
  shared
```

React owns UI state and screens. Phaser owns the live game canvas. WebSocket events are normalized in one client-side gateway and then consumed by React stores and Phaser systems.

## WebSocket Design

Server responsibilities:

- Authenticate socket connection.
- Track online player state in Redis.
- Join players into rooms.
- Validate every combat action.
- Apply cooldown and anti-spam checks.
- Calculate damage server-side.
- Update Redis atomically.
- Broadcast compact events to the room.
- Clean up on disconnect.

Client responsibilities:

- Render game state.
- Send player intentions only.
- Reconnect with session recovery.
- Never decide authoritative damage, rewards, boss HP, or ranking.

## Redis Data Model

Suggested keys:

```text
boss:{bossId}:hp
boss:{bossId}:damage
boss:{bossId}:participants
cooldown:attack:{playerId}
session:{playerId}
online:players
room:{roomId}:players
leaderboard:boss:{bossId}
jwt:blacklist:{tokenId}
ai:prompt:{hash}
ai:response:{hash}
```

Use Redis atomic operations or Lua scripts for critical gameplay updates.

## PostgreSQL Responsibility

PostgreSQL stores durable truth:

- Accounts and players.
- Inventory.
- Payments.
- Reward history.
- Achievement history.
- Quest state.
- Guild data.
- AI generated content.

It should not be queried for every attack.

## RabbitMQ Responsibility

RabbitMQ handles async workflows:

- Reward distribution after a boss dies.
- Notification jobs.
- Achievement checks.
- Ranking persistence.
- Email jobs.
- AI content generation.
- Cloudinary image generation/upload flow.
- Payment post-processing.

It must not be used for real-time combat.

## Security Baseline

The backend is authoritative.

Required security controls:

- Spring Security.
- JWT access token and refresh token rotation.
- Redis token blacklist.
- Password hashing with BCrypt or Argon2.
- CORS whitelist.
- Rate limiting.
- WebSocket authentication.
- Anti-replay nonce or timestamp for sensitive socket actions.
- Bean Validation for all inputs.
- Global exception handler with unified error responses.
- Secret loading from environment variables.
- Structured logging with sensitive value masking.

## AI Boundary

AI is never inside the gameplay loop.

AI can generate JSON for:

- Boss definitions.
- Monsters.
- Maps.
- Quests.
- Skills.
- Lore.
- Items.
- Rewards.
- Image prompts.

The backend validates AI output, stores accepted content in PostgreSQL, and caches it in Redis. Identical prompts are hashed and reused.

## First Vertical Slice

The first implementation should prove the hardest real-time path:

1. Backend project skeleton.
2. Frontend project skeleton.
3. WebSocket authentication placeholder.
4. Join boss room.
5. Attack boss.
6. Redis-backed boss HP and cooldown.
7. Broadcast boss HP and damage leaderboard.
8. Minimal Phaser scene showing boss, HP bar, players online, and attack feedback.

This slice gives us early proof of performance and architecture without prematurely building shop, payment, AI, or inventory.

## Initial Performance Strategy

- Keep WebSocket messages compact.
- Avoid database access during combat.
- Store active combat state in Redis.
- Batch or debounce non-critical broadcasts when needed.
- Use server-side cooldowns.
- Prefer simple data structures.
- Use PostgreSQL indexes for durable lookup paths.
- Keep RabbitMQ consumers isolated from real-time gameplay.

## Phase Decision

We will start implementation with the real-time boss raid vertical slice.

The next phase is database and repository scaffolding, followed by backend skeleton and WebSocket game loop.
