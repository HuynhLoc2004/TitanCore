# TitanCore Realtime World Protocol

## Status And Scope

This document defines the Phase 5.0 protocol and ownership contract for a map
with 15 Khu and 10 players per Khu. It does not approve implementation, public
API changes, Redis scripts, WebSocket infrastructure, migrations, or combat
mechanics.

## Authority Model

The client sends intentions. The server owns:

- connection identity and account status;
- map and Khu membership;
- movement acceptance and authoritative position;
- monster and boss lifecycle;
- damage, cooldowns, effects, drops, and rewards;
- chat and announcement authorization;
- sequence assignment and reconciliation.

React owns account/lobby overlays and bounded summaries. Phaser owns realtime
presentation, local prediction where later approved, interpolation, animation,
camera, and input sampling. Neither client layer writes trusted game facts.

## Connection Lifecycle

```text
authenticate socket
  -> negotiate protocol version
  -> reserve/join Khu
  -> receive map metadata
  -> receive full Khu snapshot
  -> acknowledge snapshot sequence
  -> enable intentions
  -> heartbeat and sequenced deltas
```

The socket is bound to the authenticated user session. A connection ticket is
short-lived, single-purpose, and contains no durable refresh credential.
Account and session revocation are enforced during connection and throughout
the authenticated request path.

## Message Envelope

Every message has a bounded, versioned envelope:

```json
{
  "type": "WORLD_EVENT_NAME",
  "protocolVersion": 1,
  "mapInstanceId": "opaque-id",
  "channelId": "opaque-id",
  "generation": 1,
  "sequence": 42,
  "serverTime": "2026-07-28T00:00:00Z",
  "payload": {}
}
```

Rules:

- Server events use monotonically increasing sequence numbers per Khu
  generation.
- Client intentions carry an opaque intention ID for bounded idempotency.
- Payload size, array length, string length, and event rate are bounded.
- Unknown versions or event types fail closed.
- Internal database, Redis, socket, and infrastructure identifiers are not
  exposed when a safe opaque identifier suffices.
- Sensitive auth values never appear in message bodies or logs.

Exact DTO fields are approved only with the implementation PR.

## Event Families

### Server To Client

- `WORLD_SNAPSHOT`: authoritative current-Khu entities and sequence.
- `ENTITY_SPAWNED`, `ENTITY_UPDATED`, `ENTITY_REMOVED`.
- `PLAYER_JOINED`, `PLAYER_LEFT`.
- `COMBAT_EVENT`: bounded animation/result facts, never trusted client damage.
- `BOSS_ANNOUNCEMENT`: map-level boss Khu and lifecycle.
- `CHANNEL_OCCUPANCY`: bounded map-level availability summary.
- `CHANNEL_TRANSFER_STARTED`, `CHANNEL_TRANSFER_COMPLETED`,
  `CHANNEL_TRANSFER_REJECTED`.
- `CHAT_MESSAGE`: current-Khu player chat after validation.
- `SYSTEM_ANNOUNCEMENT`: server-only map or Khu notice.
- `LOOT_CONFIRMED`: durable grant presentation data.
- `RECONCILE_REQUIRED`, `WORLD_CLOSED`.

### Client To Server

- `SNAPSHOT_ACK`.
- `MOVE_INTENTION`.
- `AIM_INTENTION`.
- `ATTACK_INTENTION`.
- `SKILL_INTENTION`.
- `DODGE_INTENTION`.
- `INTERACT_INTENTION`.
- `CHANNEL_TRANSFER_REQUEST`.
- `CHAT_SEND`.
- `HEARTBEAT`.

Clients cannot send spawn, HP, damage, reward, loot, occupancy, announcement, or
finalization events.

## Sequence And Reconciliation

- A client applies only events for its current map, Khu, and generation.
- Duplicate or old sequence numbers are ignored.
- A small gap triggers bounded replay when retained.
- A large or unavailable gap triggers a fresh snapshot.
- A channel switch increments the local connection generation and rejects late
  source-Khu events.
- Animation prediction never commits inventory, reward, health, or cooldown
  truth.

The event retention window is bounded by memory and reconnect objectives. Exact
duration requires load measurements.

## Redis Ownership

Redis stores transient realtime state. PostgreSQL stores durable identity,
content versions, sessions, battle results, rewards, and audit facts.

Proposed key families:

```text
world:map:{mapInstanceId}:channels
world:map:{mapInstanceId}:boss
world:channel:{channelId}:meta
world:channel:{channelId}:players
world:channel:{channelId}:entities
world:channel:{channelId}:seq
world:channel:{channelId}:events
world:channel:{channelId}:chat
world:channel:{channelId}:reservation:{reservationId}
world:channel:{channelId}:intent:{playerId}:{intentionId}
presence:player:{playerId}
ws:player:{playerId}
```

These are future protocol proposals, not approved Redis implementation.

All keys used by one atomic operation must share one Redis Cluster hash tag.
Before implementation, `channelId` must be encoded so the same Khu produces the
same tag across metadata, membership, occupancy, sequence, reservation, and
idempotency keys. Cross-Khu transfer cannot be falsely described as one
Redis-Cluster Lua transaction; it requires a reservation/transfer state
machine with compensating expiry.

## Atomic Admission

Admission atomically:

1. validates Khu lifecycle and capacity;
2. removes an expired reservation count if applicable;
3. creates one short-lived reservation;
4. returns occupancy and reservation generation.

Final join atomically consumes the reservation and adds membership. Duplicate
completion is idempotent. Disconnect cleanup and reservation expiry reconcile
the count from membership rather than trusting an independently drifting
counter.

## Movement And Combat Traffic

The implementation must use bounded fixed-rate input sampling and server
simulation. It must not send one network message per browser input event or
render frame.

- Movement intentions describe normalized direction and input sequence.
- Server snapshots/deltas are broadcast at a measured fixed rate.
- Client rendering interpolates between server states.
- Optional local movement prediction requires later approval and reconciliation
  tests.
- Combat intentions reuse room/Khu-scoped cooldown and idempotency principles
  from `REDIS.md`.

Target rates are not fixed in documentation before profiling. The load test
must evaluate at least 150 users, realistic movement, monster activity, chat,
and one boss Khu.

## Chat Isolation

Chat uses an independently rate-limited path:

- authenticated and ACTIVE account required;
- current Khu membership required;
- mute and content length rules enforced server-side;
- messages receive server timestamps and safe display identity;
- no HTML, executable markup, arbitrary links, or client-selected global scope;
- logs and metrics avoid message content unless a separately approved
  moderation policy requires bounded retention.

Chat congestion cannot delay movement or combat dispatch. A separate bounded
queue or priority class is required.

## Loot Delivery

`LOOT_CONFIRMED` is emitted only after durable reward processing succeeds. It
contains a grant/reference identifier, safe published item reference, quantity
or amount shape, and presentation rarity. Redelivery is idempotent at the
logical grant level and may replay presentation only according to a bounded
client notification policy.

Redis presence locates the online route; the WebSocket service sends realtime
delivery. Redis never sends the client notification.

## Rate Limits And Abuse

Rate limits are scoped by private HMAC-derived account/session keys and, where
appropriate, Khu:

- connection and join attempts;
- channel switching;
- movement/input frequency;
- attack and skill intentions;
- chat;
- heartbeat anomalies;
- invalid protocol messages.

Client IP is resolved only through the trusted proxy policy. Redis unavailable
must not silently disable combat legality, admission capacity, chat moderation,
or authentication checks. Failure policy is explicit per operation and
defaults closed for state-changing actions.

## Backpressure

Outbound messages are classified:

1. critical lifecycle, reconciliation, and combat facts;
2. player/entity movement deltas;
3. chat and loot presentation;
4. decorative ambient synchronization.

Queues are bounded. Decorative updates may be coalesced or dropped first.
Critical events trigger reconnect/snapshot rather than unbounded buffering.
Slow clients cannot consume server memory indefinitely or reduce the Khu tick
rate for other players.

## Observability

Collect non-sensitive metrics:

- connected sockets and authenticated players;
- occupancy/reservation mismatch;
- join and transfer latency;
- input rejection reason counts;
- Khu tick duration;
- outbound queue depth and dropped/coalesced updates;
- reconnect and snapshot frequency;
- Redis latency/error rate;
- boss announcement fan-out;
- chat rejection and rate-limit counts.

Do not log raw access tokens, refresh tokens, cookies, chat payloads, private
rate-limit subjects, or complete intention payloads.

## Redis Loss And Recovery

Phase 5.0 does not claim transparent reconstruction of every active entity.

- New joins and state-changing intentions fail closed.
- Connections enter a bounded reconnecting state.
- If Redis recovers within the approved grace window, ownership and generation
  are revalidated before a fresh snapshot.
- If authoritative Khu state is unrecoverable, close the Khu, retain durable
  confirmed grants, cancel unresolved transient actions, and route players to a
  safe React recovery screen.
- Compensation, if warranted, is durable, auditable, and asynchronous.

The implementation phase must approve exact grace periods, persistence mode,
and operational runbook before production.

## Security Acceptance

The protocol is not ready until tests prove:

- atomic capacity under concurrent joins;
- no replayed intention applies twice;
- cross-Khu events are rejected after transfer;
- revoked sessions and inactive accounts are disconnected;
- spoofed forwarding headers do not change identity or rate-limit scope;
- Redis failure cannot bypass validation;
- slow-client backpressure remains bounded;
- malformed and oversized messages fail safely;
- chat cannot publish globally;
- a client cannot claim damage, loot, boss state, or occupancy.
