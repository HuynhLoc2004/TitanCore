# Phase 5 Realtime World Roadmap

## Status

This roadmap decomposes the owner-approved realtime map vision into reviewable
phases. It does not approve implementation automatically. Each architecture
change, dependency, schema, public API, WebSocket design, Redis design, asset
source, audio source, and production deployment still requires the Project
Owner's explicit approval before implementation.

## Product Outcome

The first playable realtime slice is:

```text
login
  -> profile onboarding
  -> dynamic Raid Camp
  -> enter published map
  -> server places player into one of 15 Khu
  -> move with desktop or mobile controls
  -> see and chat with up to nine other players
  -> fight ordinary monsters
  -> receive map-wide boss announcement
  -> switch to the boss Khu when space permits
  -> fight with authoritative combat
  -> receive confirmed loot through the flying companion
  -> return to a durable result and inventory state
```

## Phase 5.0: Architecture Contract

Deliverables:

- world map and Khu ownership;
- placement and switching policy;
- realtime protocol and Redis boundary;
- desktop/mobile unified input;
- animated map, character, monster, boss, VFX, audio, and performance gates;
- implementation sequencing and load-test acceptance.

No runtime code or schema is included.

## Phase 5.1: Animation Production Proof

Small PR sequence:

1. Runtime asset cleanup and atlas metadata for one Core Raider.
2. Phaser-only preview scene with idle, move, attack, hit, and defeat/recovery
   animation states.
3. One ordinary monster animation set.
4. Rubber Duck King telegraph and attack proof.
5. One map ambience layer proof.

Required approvals before work:

- runtime asset production method and source/license;
- any animation tooling or dependency;
- final frame dimensions, atlas format, and pivot convention;
- audio source/license if audio enters this phase.

Acceptance:

- no static-cutout bobbing presented as final animation;
- alpha, pivot, frame bounds, and atlas checks pass;
- 60fps desktop and stable 30fps target-device proof;
- reduced-motion behavior;
- no network, schema, or combat rules.

## Phase 5.2: World Runtime Foundation

Architecture approval required for:

- Phaser scene lifecycle;
- spatial partitioning;
- entity interpolation;
- fixed-rate simulation contract;
- map manifest and asset loading;
- unified input adapters;
- loading and recovery states.

Initial implementation is one offline/local Khu with one hero, one ordinary
monster, and ambient map layers. It proves presentation and lifecycle without
pretending multiplayer is complete.

## Phase 5.3: Realtime Protocol And Presence

Separate backend and frontend PRs:

1. Versioned WebSocket envelope and authenticated connection.
2. Heartbeat presence and Khu snapshot/delta flow.
3. Atomic admission for 15 Khu x 10 players.
4. Bounded reconnect, sequence reconciliation, and backpressure.
5. Current-Khu-only visibility.

Required approvals:

- WebSocket library/configuration if changed;
- exact Redis key schema and Lua/state-machine operations;
- public message contracts;
- rate limits and failure policy;
- observability and load-test environment.

No combat or loot is added until admission, isolation, and reconnect tests pass.

## Phase 5.4: Channel Transfer And Communication

Deliverables:

- 20/80 placement policy;
- atomic destination reservation and safe source release;
- portal/loading transition;
- switch cooldown and combat/down/reward locks;
- current-Khu chat;
- map-wide boss/system announcements;
- moderation and backpressure boundaries.

No queue is added for a full boss Khu.

## Phase 5.5: Ordinary Monster Vertical Slice

Deliverables:

- approved monster definition and spawn contract;
- server-authoritative movement/combat intentions;
- one ordinary monster family;
- hitbox and telegraph design;
- one basic attack, dodge, and one skill;
- animated hit/defeat feedback;
- measured desktop/mobile input;
- no client-trusted damage or reward.

Combat formulas, statistics, drop tables, and content definitions require a
dedicated owner review before implementation.

## Phase 5.6: Boss Khu Vertical Slice

Deliverables:

- boss spawn ownership in one Khu;
- map announcement;
- admission under contention;
- Rubber Duck King encounter using pinned content/assets;
- one-time finalization;
- durable result and outbox reward event;
- reconnect and full-Khu behavior.

The implementation reuses approved Phase 2 idempotency and reward architecture.
It does not query PostgreSQL or RabbitMQ per attack.

## Phase 5.7: Loot Companion And Reward Presentation

Deliverables:

- presentation event only after durable grant confirmation;
- queue/coalescing;
- item, quantity, and rarity presentation;
- animation, audio, reduced motion, and accessible text;
- idempotent reconnect behavior;
- no inventory mutation from the client.

## Phase 5.8: Multiplayer Performance Gate

Required scenarios:

- 150 authenticated simulated users in one map instance;
- 15 Khu at capacity;
- movement and ordinary monster traffic in every Khu;
- one active boss Khu;
- Khu switching contention;
- chat and map announcements;
- slow clients, reconnects, and Redis latency/failure;
- low-end mobile rendering with 10 players and active effects.

Measure:

- server tick duration;
- intention validation latency;
- WebSocket fan-out and queue depth;
- Redis operation latency and memory;
- backend CPU and heap;
- reconnect success;
- client FPS, frame-time percentiles, memory, and asset load time;
- rejected/dropped/coalesced traffic.

Release fails if capacity, security, correctness, or critical visual clarity is
preserved only by unbounded queues, hidden errors, or client authority.

## Dynamic Content And Admin

Maps, monsters, bosses, items, rewards, announcements, events, animation
manifests, and audio references are published data. Stable engine rules remain
code-owned.

Admin implementation is separately approved and must support:

- draft, review, preview, publish, schedule, archive, and rollback-as-new-version;
- immutable audit;
- human approval for AI-generated assets;
- active-map version pinning;
- no direct mutation of active Redis state.

No generated asset is production content merely because it appears in a review
pack.

## External Services And Keys

No new key is required for Phase 5.0.

Later approval checkpoints:

| Need | Earliest phase | Configuration category |
| --- | --- | --- |
| Object storage local | Asset pipeline implementation | MinIO endpoint and local credentials |
| Object storage production | Production asset delivery | R2/S3 endpoint, bucket, access credentials |
| CDN | Production asset delivery | Public asset base and cache policy |
| Licensed/generated audio | Audio production | Provider/license records; API key only if an approved provider requires it |
| AI asset drafts | Content production tooling | Approved provider/model configuration |
| Realtime load testing | Phase 5.3/5.8 | Isolated test environment endpoints and credentials |

Values must never be requested before their approved phase or committed to the
repository.

## Adversarial Guardrails

- Do not hardcode maps, bosses, monsters, items, rewards, banners, or events in
  components.
- Do not pass remote HTML, CSS, scripts, routes, commands, or permissions to the
  client registry.
- Do not call PostgreSQL, RabbitMQ, AI, Admin, or object storage per movement or
  attack.
- Do not synchronize all 15 Khu to every client.
- Do not use a moving PNG as the final character, monster, or boss animation.
- Do not let decorative motion obscure telegraphs or overload low-end devices.
- Do not accept client damage, position, cooldown, drop, or reward facts.
- Do not let channel switching duplicate presence, combat, or rewards.
- Do not let chat or slow clients block the combat path.
- Do not mutate active instances when content is published or rolled back.
- Do not introduce audio, asset-provider, WebSocket, Redis, or schema changes
  without the required owner approval.

## Owner Approval Gates

Before each implementation phase, present:

1. exact scope and exclusions;
2. architecture/dependency/API/schema options;
3. advantages, disadvantages, and recommendation;
4. asset/audio source, license, cost, and configuration needs;
5. security and failure behavior;
6. performance budget and test plan;
7. branch and PR breakdown.

Implementation stops at the PR. Merge remains the Project Owner's decision.
