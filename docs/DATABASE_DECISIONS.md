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

RabbitMQ publication for battle completion and payment processing is driven from durable PostgreSQL state through `outbox_events`.

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

## Decision 10: Transactional Outbox

Battle completion and payment processing must persist their durable state and the matching `outbox_events` row in the same PostgreSQL transaction.

Why:

- RabbitMQ is at-least-once and not transactional with PostgreSQL.
- Publishing directly after a database commit can lose events if the process crashes.
- Outbox polling makes publication retryable and auditable.

## Decision 11: Immutable Reward Ledger

Every granted reward must be recorded in `reward_ledger`.

Why:

- Reward claims show eligibility and claim status.
- Inventory rows show current ownership.
- The ledger shows immutable historical grant facts for audit, support, and anti-cheat review.

## Decision 12: Currency Deferred

Currency ownership is deferred to the Game Economy phase.

Why:

- Phase 1 MVP is boss-room combat and reward foundation.
- Currency balances and ledgers are economy-critical and require separate approval.
- Phase 2.5 must not implement currency behavior unless explicitly scoped.

## Decision 13: AI Generated Content Metadata Boundary

`ai_generated_content` stores generation metadata and validated JSON payloads only.

Why:

- AI-generated data must be validated before becoming a domain definition.
- Published domain data remains in tables such as `bosses`, `items`, `quests`, and `rewards`.
- AI never runs inside gameplay.

## Decision 14: Admin Operations Are Future Proposals Until Approved

Phase 2.3 documents admin operations, RBAC, moderation, content publishing, payment operations, email operations, feature flags, and anti-cheat investigation as architecture guidance only.

Why:

- Admin features are important but should not expand Phase 2.5 beyond the approved database implementation scope.
- The MVP targets 100-200 concurrent users and should avoid premature table explosion.
- Generalized action and version records are preferred over many specialized tables when admin implementation is later approved.
- Admin operations must be isolated from Redis and WebSocket combat paths.

Rules:

- Do not create admin migrations, entities, APIs, services, controllers, WebSocket handlers, or UI from Phase 2.3 without explicit approval.
- Treat all Phase 2.3 schema ideas as future proposals, not approved migrations.
- Sensitive admin actions require least-privilege permissions, immutable audit records, and step-up authentication when implemented.

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
- Transactional outbox now has a concrete table design.
- Immutable reward ledger now records every reward grant.
- Currency is explicitly deferred to avoid ambiguous ownership.
- AI-generated content metadata is separated from published domain definitions.
- Admin operations and RBAC are documented as future architecture without approving schema or implementation work.

Improvement made during review:

- Battle `damage_logs` is documented as sampled/finalized instead of per-hit by default to protect write throughput.
- Redis damage sorted sets are the primary live damage mechanism.
- Payment and reward idempotency are explicit requirements.
- Boss HP and damage keys were changed from boss-scoped to room-scoped.
- Permanent online presence was replaced with heartbeat-based expiring presence.
- Reward claim `source_id` is non-null to make PostgreSQL idempotency reliable.
- Cooldown and attack idempotency keys are room-scoped and Redis Cluster hash-slot compatible.
