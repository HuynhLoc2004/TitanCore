# TitanCore Game Shell Architecture

## Status And Scope

This document defines the approved boundary between the React application shell
and the Phaser combat renderer. It does not approve implementation code, APIs,
migrations, UI, WebSocket handlers, Redis combat logic, or Admin tooling.

TitanCore targets 100-200 concurrent players. The first room target is eight
players. Future room limits must be configurable and validated by the server.

## Runtime Ownership

### React Owns

- authentication, OAuth completion, and account security;
- profile onboarding and player identity;
- lobby, room discovery, inventory, equipment, rewards, and settings;
- future Admin experiences;
- route guards, loading/error/empty states, and accessibility;
- published shell configuration and registered-section composition;
- mounting and disposing the stable combat host.

### Phaser Owns

- combat scenes, cameras, rendering, and input sampling;
- character, boss, map, projectile, telegraph, and VFX presentation;
- synchronized combat animation and audio;
- interpolation and reconciliation of authoritative room events;
- combat quality tiers and canvas lifecycle.

### Backend Owns

- room membership and lifecycle;
- damage, boss HP, cooldowns, reward eligibility, and ranking;
- account and session authorization;
- attack validation and idempotency;
- active-room content version and asset-manifest pinning.

Phaser sends intentions only. It must never calculate trusted damage, rewards,
boss life, cooldown completion, or durable ranking.

## Stable GameHost

React mounts one `GameHost` for an entered room. Routine React renders must not
recreate the Phaser instance.

The host receives:

- authenticated room ticket;
- pinned content-version identifiers;
- immutable asset-manifest identifier;
- accessibility and quality settings;
- normalized WebSocket event adapter;
- intention gateway;
- lifecycle callbacks for loading, ready, reconnecting, failed, and disposed.

The host must:

- initialize once per room entry;
- attach listeners once;
- pause or sleep on visibility changes;
- release scenes, timers, audio, input, textures, and subscriptions on exit;
- reject late events from a previous room generation;
- avoid storing TitanCore access or refresh tokens.

## Client Data Flow

```text
React route and session
  -> room selection
  -> backend returns room ticket + pinned versions
  -> React loads the combat module
  -> GameHost loads the immutable asset manifest
  -> authenticated WebSocket joins the room
  -> normalized events feed React summaries and Phaser systems
  -> Phaser sends movement, attack, and skill intentions
```

React state updates must not run at the render-frame frequency. The WebSocket
gateway normalizes messages once. Phaser consumes high-frequency room events;
React consumes bounded summaries such as connection state, room result, and
accessible announcements.

## Lazy Loading And Route Splitting

- Auth and OAuth completion remain in the application boot chunk.
- Lobby, inventory, profile, and combat are separate lazy route boundaries.
- Phaser is not loaded on login, onboarding, or ordinary lobby navigation.
- Prefetch the combat code after an eligible room is selected or when idle
  network conditions permit.
- Load only the selected room manifest and scene-specific atlases.
- Cancel prefetch when selection changes.
- Do not preload future bosses, maps, seasons, or events globally.

## Safe Dynamic Lobby

Frequently changed menus, banners, registered sections, bosses, maps, items,
rewards, quests, events, seasons, cosmetics, and announcements must not be
hardcoded in React components.

Remote configuration may control:

- approved label keys and localized copy;
- order and visibility;
- schedule and audience rules;
- references to published domain content;
- composition using registered section identifiers.

Remote configuration must never provide:

- JavaScript, HTML, CSS, templates, or executable expressions;
- arbitrary component names;
- arbitrary routes or external redirects;
- permissions or authorization decisions;
- unvalidated asset URLs;
- direct Redis, WebSocket, payment, or Admin commands.

React owns an allowlisted section registry. Unknown section types fail closed
and produce an observable configuration error. Route registration and security
remain code-owned.

## Dynamic Versus Code-Owned Matrix

| Concern | Dynamic published content | Stable code-owned behavior |
| --- | --- | --- |
| Menus/navigation | Label, order, visibility | Route registry, guards, permissions |
| Banners | Copy, art reference, schedule | Banner component and safe actions |
| Page sections | Registered type, order, payload | Component registry and validation |
| Bosses/maps/items | Versioned definitions and assets | Renderer systems and schemas |
| Rewards/events/seasons | Published definitions | Eligibility/security boundaries |
| Quests | Deferred published definitions | Progression validation and state rules |
| Cosmetics | Definition, art, availability | Entitlement and rendering slots |
| Announcements | Localized copy and schedule | Sanitized presentation |
| Combat | Pinned definition input | Validation, reconciliation, intention rules |

## Profile Onboarding

Generated OAuth usernames remain internal account identifiers and must not be
the primary displayed game identity.

Approved display-name policy:

- 3-24 visible graphemes;
- Unicode normalization;
- case-insensitive uniqueness;
- reserved-name, moderation, and control-character validation;
- bounded collision retries with clear, non-enumerating feedback.

Recommended future implementation requires explicit approval:

- a forward migration that records profile onboarding completion;
- an authenticated profile API with optimistic version checking;
- a React onboarding route before lobby entry;
- a rename policy and cooldown decision.

Comparing a generated username to `player_profiles.display_name` is not a safe
completion signal. Authentication, logout, and account security remain
available even when onboarding is incomplete.

## Lobby Composition

Desktop layout:

1. Player identity header, settings, and notifications.
2. Scheduled event/banner region.
3. Dominant boss-room list or grid.
4. Character preview and equipment summary.
5. Inventory/reward preview.

Mobile portrait layout:

1. Compact identity header.
2. Active event or announcement.
3. Boss rooms.
4. Character preview.
5. Inventory/reward preview.

Room cards show approved boss art, room state, difficulty, participant count,
capacity, availability, and one clear join action. Layout dimensions remain
stable through loading. Empty states provide one relevant action. Cached
published data may remain visible during recoverable failures.

## First Eight-Player Slice

```text
login
  -> profile onboarding
  -> dynamic lobby
  -> select an eight-player boss room
  -> load pinned character/map/boss manifest
  -> join authenticated room
  -> simple movement
  -> basic attack
  -> one skill
  -> avoid boss telegraphs
  -> server finalizes battle once
  -> React presents durable reward result
```

The first slice does not approve detailed classes, PvP, complex movement,
multiple skills, seasons, quests, economy, or a full Admin UI.

## Version Pinning

Room creation stores the published content versions and asset-manifest version
used by that room. All participants load the same immutable versions.

- Publishing affects newly created rooms only.
- Rollback publishes a new known-good version for future rooms.
- Admin operations never mutate active battle Redis keys.
- An active room remains playable while newer content is published.
- Required pinned assets remain retained until no active or recoverable room
  references them.

## Realtime Isolation

The attack path is:

```text
client intention
  -> authenticated WebSocket handler
  -> server validation and damage calculation
  -> atomic room-scoped Redis update
  -> sequenced room broadcast
```

There is no PostgreSQL query, RabbitMQ publication, Admin operation, AI call,
object-storage call, or content publish lookup per attack.

## Performance Budgets

- 60fps target: 16.7ms total frame time.
- Low-end fallback: stable 30fps within 33.3ms.
- Low-end mobile runtime memory target: 128MB.
- Desktop runtime memory target: 256MB.
- Lobby initial assets target: 1.5MB.
- Application shell JavaScript target: 250KB gzip.
- Lazy combat engine/code target: 350KB gzip.
- First room assets target: 5MB mobile and 10MB desktop.
- DPR cap: 2.

These are measurable targets, not permission to reduce critical clarity or
asset quality blindly. Profile first, identify the actual bottleneck, then
degrade decorative effects in the order defined by the Visual Bible.

## Reconnect And Failure

- Loading has explicit manifest, asset, socket, and scene-ready stages.
- Provider or CDN timeout produces bounded retry and a safe lobby return.
- A reconnecting client freezes intentions, keeps a visible status, and
  reconciles using battle sequence/version before resuming.
- A stale room ticket or incompatible manifest fails closed.
- Missing optional assets use reviewed fallbacks; missing critical boss, map,
  player, or telegraph assets prevent room entry.
- Background tabs sleep the scene and suspend nonessential audio.
- WebGL failure may use an approved fallback renderer only if the vertical slice
  remains readable and supported.

## Admin Content Lifecycle

Future Admin content follows:

```text
DRAFT -> IN_REVIEW -> APPROVED -> SCHEDULED/PUBLISHED -> ARCHIVED
```

Preview, rollback-as-new-version, immutable version history, and audit are
required. For the solo-owner MVP, the owner may author and publish after recent
step-up authentication, with immutable audit. Dual approval remains deferred
for ordinary content but is later required for refunds and high-risk economy
operations.

## External Configuration Timeline

No keys are required by Phase 4.0.

Future object-storage work requires provider-neutral endpoint, region, bucket,
access-key, secret-key, path-style, and public delivery base URL settings.
Production R2 and CDN values are configured only in the approved storage phase.
AI and audio-generation provider credentials are deferred to their own phases.

Expected non-secret variable names for the future storage phase are:

- `OBJECT_STORAGE_ENDPOINT`
- `OBJECT_STORAGE_REGION`
- `OBJECT_STORAGE_BUCKET`
- `OBJECT_STORAGE_ACCESS_KEY`
- `OBJECT_STORAGE_SECRET_KEY`
- `OBJECT_STORAGE_PATH_STYLE`
- `ASSET_PUBLIC_BASE_URL`

Production may bind these provider-neutral settings to R2. No value or key is
requested in Phase 4.0.

## Phase 4.0 Exclusions

This document does not approve:

- application or Phaser code;
- UI or visual assets;
- APIs or migrations;
- WebSocket or Redis combat implementation;
- Admin UI or content publishing implementation;
- MinIO, R2, CDN, Docker, or infrastructure changes;
- audio or AI generation;
- gameplay beyond the stated vertical-slice boundary.

## Related Documents

- [Visual Bible](VISUAL_BIBLE.md)
- [Asset Pipeline](ASSET_PIPELINE.md)
- [Phaser Renderer ADR](adr/ADR-PHASER-RENDERER.md)
- [Admin Operations](ADMIN_OPERATIONS.md)
- [Redis Architecture](REDIS.md)
- [Performance Rules](PERFORMANCE_RULES.md)
