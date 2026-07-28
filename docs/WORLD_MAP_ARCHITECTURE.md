# TitanCore World Map Architecture

## Status And Scope

This document defines the owner-approved Phase 5.0 realtime world model. It is
architecture guidance only. It does not approve migrations, APIs, WebSocket
handlers, Redis scripts, Phaser scenes, monsters, combat formulas, production
assets, audio, or Admin implementation.

TitanCore is a shared realtime adventure map, not a sequence of isolated boss
menus. Players explore, meet other players, fight ordinary monsters, move
between areas, and react when a boss appears.

## Approved World Model

One published map contains 15 realtime channels called `Khu` in player-facing
Vietnamese copy. Each Khu:

- has a hard capacity of 10 connected players;
- runs the same pinned map and content versions;
- has independent player, monster, drop, and transient combat state;
- supports movement, ordinary monster combat, and local chat;
- may host the map boss only when selected as the active boss Khu.

The initial concurrency envelope is 150 connected players per map. Capacity is
enforced by the server through atomic reservations, not by client counters.

For the shared-world slice, the approved 10-player Khu capacity supersedes the
earlier eight-player boss-room target in `GAME_SHELL_ARCHITECTURE.md`. The
earlier document remains a record of the original isolated vertical-slice
target; it must not be used to configure Phase 5 map admission.

`mapId`, `mapInstanceId`, and `channelId` are distinct:

- `mapId` identifies the published map definition.
- `mapInstanceId` identifies one running copy of that map when future scale
  requires more than 150 players.
- `channelId` identifies one of the 15 Khu inside that running map.

The first implementation may operate one map instance. Keeping these identities
separate prevents a future capacity increase from changing gameplay semantics.

## Placement Policy

When a player enters a map, the server chooses only among non-full Khu:

1. With 20 percent probability, choose uniformly among populated Khu containing
   6-9 players.
2. With 80 percent probability, choose uniformly among all non-full Khu.
3. If the selected pool is empty, fall back to all non-full Khu.
4. Atomically reserve one slot; if contention makes it full, retry with a
   bounded candidate set.
5. If every Khu is full, fail with a capacity response and a bounded retry hint.

The client never chooses its initial Khu and never trusts displayed occupancy
as an admission guarantee. Random selection uses a server-owned secure or
well-seeded generator; it does not need cryptographic fairness.

The 20/80 weights, dense range, Khu count, and capacity are stable approved MVP
rules. A future Admin setting may expose them only after validation, rollout,
audit, and active-instance pinning are designed. They must not be editable in a
way that mutates a running map.

## Khu Switching

Players may request another Khu within the same map instance.

The server rejects switching when:

- the destination is full or unavailable;
- the player is in combat;
- the player is downed;
- reward or loot resolution is pending;
- a switch cooldown is active;
- the current session no longer owns the player connection.

An approved switch is an atomic transfer:

```text
reserve destination
  -> mark transfer generation
  -> remove source membership
  -> join destination
  -> send authoritative destination snapshot
  -> enable intentions after snapshot acknowledgement
```

The destination reservation expires if the transfer does not complete. A
failed transfer keeps or safely restores the source membership. The client
shows a short portal/loading transition and cannot send combat intentions
during transfer.

The exact cooldown duration and portal animation are deferred to measured
gameplay tuning. The rule itself is server-owned.

## Boss Presence

At most one Khu in a map instance hosts the active map boss.

- Boss selection and spawn are server-authoritative.
- Every Khu receives a map-wide system announcement naming the boss Khu.
- Only players admitted to that Khu receive its full boss state.
- A full boss Khu has no queue in the MVP.
- Players who cannot enter continue ordinary monster play elsewhere.
- Boss defeat and reward finalization remain durable and idempotent.

A boss announcement is informational, not a reservation. The UI must state
when the destination becomes full without implying that entry is guaranteed.

Boss state uses a boss encounter identity separate from the channel identity.
This allows one Khu to host successive encounters without reusing stale keys or
sequences.

## Ordinary Monsters

Every Khu can host ordinary monsters independently of the boss:

- spawn definitions come from pinned, published content;
- spawn ownership, health, despawn, and rewards are server-authoritative;
- clients send intentions and render interpolated results;
- a monster entity has a Khu-scoped runtime ID and generation;
- inactive or empty Khu may use reduced simulation frequency;
- deterministic or server-recorded spawn seeds support diagnosis without
  making the client authoritative.

Exact monster families, statistics, skills, drop tables, pathfinding, and spawn
rates require later gameplay and content approval.

## Realtime Visibility

A client receives full realtime synchronization only for its current Khu.

It may receive bounded map-level summaries:

- Khu occupancy;
- current boss Khu and boss lifecycle;
- map-wide system announcements;
- destination availability;
- service degradation or maintenance state.

It does not receive positions, actions, monsters, projectiles, chat, or drops
from the other 14 Khu. This is the primary fan-out and privacy boundary.

## Chat And Announcements

The initial communication model is:

- player chat is visible only inside the current Khu;
- boss and system announcements may be broadcast to the whole map instance;
- clients cannot publish a map-wide announcement;
- moderation, length limits, rate limits, account status, and mute enforcement
  are server-owned;
- chat is never transported through combat Lua operations.

Durable chat history, direct messages, guild chat, voice chat, and full Admin
moderation implementation are deferred. Any temporary history must be bounded
and must not become a hidden permanent data store.

## Loot Companion

Confirmed loot is presented by a small flying companion above the local hero:

1. The server finalizes the eligible grant.
2. The client receives a grant notification containing safe display references.
3. The companion enters, presents item name, quantity, and rarity, then exits.
4. Multiple grants are queued or coalesced within a bounded window.

The companion:

- never announces an unconfirmed speculative drop;
- never covers boss telegraphs, health, controls, or accessibility messages;
- uses reviewed animation and audio cues;
- has a reduced-motion presentation;
- exposes equivalent accessible text;
- cannot trigger a reward or inventory mutation.

Item definitions and assets are resolved from pinned published content. The
notification contains identifiers and grant facts, not arbitrary HTML, routes,
scripts, or asset URLs.

## Animated Map Contract

A production map is a layered realtime scene, not one moving background image.
The renderer composes:

- stable collision and navigation geometry;
- parallax background and midground layers;
- wind-reactive flags, foliage, clouds, water, fire, and ambient props;
- animated player, monster, boss, skill, hit, loot, and interaction layers;
- foreground occlusion that never hides critical telegraphs;
- spatial ambience and priority-controlled sound effects.

Ambient systems are cosmetic. They cannot alter authoritative collision,
damage, targeting, cooldown, spawn, or reward state.

Each motion family has an independent quality tier. Low-end devices remove
decorative particles and distant motion before reducing gameplay readability.

## Loading And Entry

Map entry has explicit stages:

```text
authorizing
  -> reserving Khu
  -> loading pinned manifest
  -> loading critical assets
  -> connecting realtime channel
  -> applying authoritative snapshot
  -> ready
```

The loading scene shows reviewed map art and real progress categories. It must
not expose internal IDs or pretend bytes are loaded when only a timer advanced.
Optional ambience can stream after ready. Missing critical hero, monster,
collision, or telegraph assets blocks entry and returns safely to the lobby.

## Failure And Recovery

- A heartbeat timeout releases membership after a short grace period.
- Reconnect uses session, map, Khu, entity generation, and sequence checks.
- Intentions remain disabled until reconciliation completes.
- A Redis failure never promotes client state to authority.
- If transient world state cannot be recovered, the affected Khu is closed,
  grants already durably confirmed remain valid, and pending speculative state
  is cancelled.
- Players receive a safe system notice and return to a recoverable route.

The detailed Redis loss and compensation policy must be approved with the
implementation protocol. Phase 5.0 does not claim durable reconstruction of all
monster and movement frames.

## Data And Admin Boundary

Published map, monster, boss, item, reward, announcement, and asset definitions
are data-driven and versioned. Code owns:

- entity/component registries;
- validation and security boundaries;
- simulation rules;
- protocol schemas;
- collision and reconciliation behavior;
- accessibility fallbacks.

Admin publishing affects newly created map instances unless an explicitly
approved emergency operation says otherwise. Admin tools never mutate active
Redis world keys directly.

Future persistence proposals may include durable map definitions, spawn sets,
and encounter definitions. They are not approved migrations by this document.

## Acceptance Gates

World implementation cannot begin as one large PR. Before multiplayer release:

- one Khu must pass movement and animation quality review;
- occupancy reservation must pass concurrent admission tests;
- 15 Khu must pass fan-out and memory load tests;
- 150 simulated map users must meet latency budgets;
- reconnect and Redis-unavailable behavior must fail safely;
- mobile landscape controls and desktop controls must remain equivalent;
- boss announcements, local chat, and loot companion must pass abuse and
  accessibility review.
