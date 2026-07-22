# Data Flow

## Player Login

```mermaid
sequenceDiagram
    participant Client
    participant API
    participant PostgreSQL
    participant Redis

    Client->>API: Login request
    API->>PostgreSQL: Load user by email
    API->>PostgreSQL: Insert login_history
    API->>PostgreSQL: Store refresh token hash
    API->>Redis: Cache session
    API-->>Client: Access token + refresh token
```

PostgreSQL: user, login history, refresh token. Redis: active session cache and blacklist checks.

## Battle Join

```mermaid
sequenceDiagram
    participant Client
    participant WebSocket
    participant PostgreSQL
    participant Redis

    Client->>WebSocket: Join room
    WebSocket->>Redis: Validate session and online state
    WebSocket->>PostgreSQL: Read durable player/boss metadata if not cached
    WebSocket->>Redis: Add player to room set
    WebSocket-->>Client: Joined room state
```

PostgreSQL is read at room join only when metadata is not cached. Active room state lives in Redis.

## Attack Boss

```mermaid
sequenceDiagram
    participant Client
    participant WebSocket
    participant Redis

    Client->>WebSocket: Attack intention
    WebSocket->>WebSocket: Calculate and validate final damage
    WebSocket->>Redis: Atomic cooldown + attackId + HP + ranking update
    Redis-->>WebSocket: New HP and damage state
    WebSocket-->>Client: Broadcast compact update with battle sequence
```

No PostgreSQL. No RabbitMQ. No AI. Client damage is never trusted.

Atomic attack contract:

- Validate room membership and active room status.
- Validate boss is alive.
- Validate cooldown.
- Reject duplicate `attackId`.
- Apply server-calculated damage.
- Update `battle:room:{roomId}:boss:hp`.
- Update `battle:room:{roomId}:damage`.
- Increment battle sequence for reconciliation.

## Reward Distribution

```mermaid
sequenceDiagram
    participant Battle
    participant Redis
    participant RabbitMQ
    participant RewardWorker
    participant PostgreSQL
    participant NotificationQueue

    Battle->>Redis: Acquire one-time finalization lock
    Battle->>Redis: Read final damage leaderboard
    Battle->>PostgreSQL: Transaction: complete room + persist outbox reward event
    Battle->>RabbitMQ: Publish reward event from outbox publisher
    RewardWorker->>PostgreSQL: Transaction: reward claim + inventory mutation + immutable ledger
    RewardWorker->>NotificationQueue: Publish notification event
```

Reward work is asynchronous and logically exactly-once through a durable idempotency key.

## Payment Success

```mermaid
sequenceDiagram
    participant PayOS
    participant API
    participant PostgreSQL
    participant RabbitMQ
    participant PaymentWorker

    PayOS->>API: Webhook
    API->>API: Verify signature
    API->>PostgreSQL: Transaction: upsert payment + persist outbox event
    API->>RabbitMQ: Publish payment event from outbox publisher
    PaymentWorker->>PostgreSQL: Apply cosmetic entitlement
```

Payment status must be idempotent and append-auditable.

## Notification

```mermaid
sequenceDiagram
    participant Producer
    participant RabbitMQ
    participant NotificationWorker
    participant PostgreSQL
    participant Redis
    participant WebSocket
    participant Client

    Producer->>RabbitMQ: Notification event
    NotificationWorker->>PostgreSQL: Insert notification
    NotificationWorker->>Redis: Locate online presence/socket mapping
    NotificationWorker->>WebSocket: Request realtime delivery if online
    WebSocket-->>Producer: No direct callback
    WebSocket-->>Client: Send realtime notification
```

Persistent notification is stored in PostgreSQL. Redis only locates online presence; the WebSocket service sends realtime notifications.
