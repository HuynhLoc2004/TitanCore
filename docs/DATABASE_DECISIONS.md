# Database Decisions

## Decision 1: Hybrid PostgreSQL + Redis

Chosen: PostgreSQL for durable data, Redis for realtime state.

Why:

- PostgreSQL provides integrity, relational querying, transactions, and auditability.
- Redis provides low-latency atomic operations for boss HP, cooldowns, rooms, sessions, and live ranking.
- This avoids database pressure during combat.

Rejected:

- PostgreSQL-only gameplay state: too slow and lock-heavy for realtime attack loops.
- Redis-only core data: poor durability and auditability.

## Decision 2: RabbitMQ Async Only

RabbitMQ is used for rewards, notifications, payments, mail, analytics, and audit processing.

It is not used for combat actions because queue latency and retry semantics do not belong in realtime gameplay.

RabbitMQ publication for battle completion should be driven from durable PostgreSQL completion state. A transactional outbox is the preferred future implementation pattern.

## Decision 3: UUID Primary Keys

UUIDs are used for business tables.

Pros:

- Safe across distributed workers.
- Harder to enumerate than sequential IDs.
- Easy future data movement.

Cons:

- Larger indexes than bigint.

Mitigation:

- Keep hot Redis keys compact.
- Use smallint for small reference tables.

## Decision 4: Soft Delete Selectively

Soft delete is used for users, player profiles, items, bosses, and guilds.

Append-only tables like payments, login history, audit logs, battle history, and damage logs are never soft-deleted.

## Decision 5: JSONB For Flexible Definition Payloads

JSONB is allowed for metadata, requirements, reward payloads, and snapshots.

Rules:

- Do not use JSONB to avoid modeling core relationships.
- Add GIN indexes only when query patterns justify them.
- Validate JSON shape in application layer before persistence.

## Decision 6: Ranking Is Dual-Write By Stage

Live ranking uses Redis sorted sets. Durable ranking snapshots are written to PostgreSQL asynchronously.

This keeps leaderboard updates fast while preserving historical season records.

## Decision 7: Room-Scoped Battle Redis Keys

Active boss HP and damage ranking are room-scoped:

- `battle:room:{roomId}:boss:hp`
- `battle:room:{roomId}:damage`

Why:

- The same boss definition can appear in many battle rooms.
- Room-scoped keys prevent state collision between simultaneous battles.
- Finalization can be idempotent per `battle_room_id`.

## Decision 8: Heartbeat-Based Presence

Presence is based on expiring Redis heartbeat keys, not a permanent online set.

Why:

- Disconnect events are not guaranteed during network failure.
- TTL-based presence self-heals.
- Shard presence sorted sets can be reconciled by timestamp.

## Decision 9: Logical Exactly-Once Rewards

RabbitMQ is at-least-once. TitanCore achieves logical exactly-once reward processing with PostgreSQL idempotency.

Required unique key:

```text
reward_claims(player_id, reward_id, source_type, source_id)
```

Reward claim creation, inventory mutation, and immutable ledger insertion must happen in one PostgreSQL transaction.

## Engineering Review

Reviewed as Principal Database Architect:

- Normalization is appropriate for account, inventory, payment, and social data.
- Realtime gameplay state is not stored in PostgreSQL per attack.
- RabbitMQ is not in the gameplay loop.
- Indexes are tied to expected query paths, not added blindly.
- Append-only audit/payment/history tables preserve traceability.
- Soft delete is limited to recoverable business entities.
- Future AI-generated content can reuse item, boss, quest, and reward definitions without schema redesign.
- Room-scoped Redis battle keys prevent collisions between simultaneous boss rooms.
- Heartbeat-based presence avoids stale permanent online state.
- Reward processing has a clear logical exactly-once strategy.
- Reliable reward publication requires durable completion state before RabbitMQ publish.

Improvement made during review:

- Battle `damage_logs` is documented as sampled/finalized instead of per-hit by default to protect write throughput.
- Redis damage sorted sets are the primary live damage mechanism.
- Payment and reward idempotency are explicit requirements.
- Boss HP and damage keys were changed from boss-scoped to room-scoped.
- Permanent online presence was replaced with heartbeat-based expiring presence.
