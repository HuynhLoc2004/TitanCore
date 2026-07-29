# ADR: Three.js Stylized 3D World Renderer

- Status: Accepted
- Scope: Phase 5 gameplay presentation pivot
- Decision owner: TitanCore Project Owner
- Supersedes: `ADR-PHASER-RENDERER.md` for released world gameplay

## Context

The Phaser proof established loading, fixed-step simulation, unified input,
quality tiers, local combat feedback, and manifest boundaries. Product review
also established that a fixed 2.5D side view cannot deliver TitanCore's
approved experience:

- a freely explorable stylized world with vertical terrain;
- a third-person camera that may orbit the hero through 360 degrees;
- grounded movement, jumping, falling, gliding, and reviewed aerial skills;
- skeletal heroes, monsters, bosses, NPCs, equipment, and expressive motion;
- spatial combat with readable telegraphs and satisfying audiovisual impact;
- up to ten visible players in one Khu without abandoning low-end mobile.

TitanCore is not a clone of another game. It adopts the interaction quality
expected of a modern third-person action RPG while preserving TitanCore's
cartoon humor, connected hunting worlds, Khu model, story, progression, and
server authority.

## Options

### Continue With Phaser 2.5D

Advantages:

- existing implementation and lower short-term asset cost;
- predictable 2D memory and fill-rate budgets.

Disadvantages:

- cannot provide genuine orbiting perspective or spatial elevation;
- sprite-facing and layered backgrounds remain visibly constrained;
- continued investment would polish the wrong primary experience.

### Three.js With A TitanCore-Owned Runtime Layer

Advantages:

- direct WebGL scene, camera, animation, material, instancing, culling, and
  glTF control;
- supports stylized 3D without coupling application state to a scene framework;
- keeps the engine boundary small and measurable;
- supports browser, desktop wrapper, and future mobile wrapper targets.

Disadvantages:

- TitanCore must own scene lifecycle, input integration, collision adapters,
  pooling, loading, and performance instrumentation;
- production-quality 3D requires reviewed models, rigs, animation, lighting,
  VFX, audio, and optimization rather than generated still images;
- physics and tooling dependencies require separate approval when introduced.

### A Full General-Purpose Engine Export

Advantages:

- mature editor, animation, physics, lighting, and asset workflows.

Disadvantages:

- larger download and memory baseline for the browser target;
- more difficult React/auth/session integration and web deployment;
- a provider-specific pipeline would be adopted before the vertical slice
  proves its need.

## Decision

Use Three.js as TitanCore's primary released-world renderer.

- React owns authentication, onboarding, lobby, inventory, settings, bounded
  overlays, and future Admin UX.
- Three.js owns the loaded world scene, third-person camera, skeletal animation,
  local presentation, interpolation, VFX, spatial audio emitters, and rendering.
- The renderer is lazy-loaded only when entering a world.
- Phaser remains a prototype/reference implementation until its useful
  contracts are migrated. It is not the released gameplay target.
- The backend remains authoritative for movement legality, combat, cooldowns,
  health, rewards, Khu membership, progression, and published content.
- Client prediction and interpolation may improve feel but never become trusted
  state.
- Runtime world assets use reviewed glTF/GLB with provider-neutral object keys.
  Images, models, animation clips, and audio are not stored in PostgreSQL.
- The initial renderer dependency is Three.js only. A physics engine, React
  scene wrapper, compression decoder, audio middleware, or editor integration
  requires a measured need and a separately reviewed dependency decision.

## Camera And Movement

The primary camera is a third-person perspective camera:

- orbit yaw supports a full 360-degree range;
- pitch and zoom are bounded to preserve readability and avoid terrain clipping;
- collision prevents the camera passing through solid world geometry;
- camera-relative movement is supported on keyboard, gamepad, and touch;
- mobile uses a left movement joystick and right camera-look region;
- lock-on or target assist, when approved, never overrides a clear player
  selection or targets an invalid entity.

The initial vertical slice proves:

1. walk, run, turn, jump, fall, land, and one bounded glide/hover state;
2. terrain height, ramps, solid obstacles, ledge rules, and safe respawn;
3. one grounded attack and one reviewed skill with anticipation, release,
   impact, and recovery;
4. one NPC and multiple ordinary monsters;
5. a local representation of additional players before networking is enabled.

Unrestricted flight is not client-owned movement. Every aerial state has
server-approved entry, duration, velocity, collision, and exit rules.

## Art And Animation Contract

- Use stylized proportions, cel/toon materials, authored silhouettes, controlled
  outlines, and readable color grouping.
- Heroes, NPCs, monsters, and bosses use skeletal rigs and named animation
  clips. Moving a static cutout is never accepted as final character animation.
- Animation graphs include locomotion blending, directional turns, vertical
  transitions, attack anticipation, impact, recovery, hit reaction, and defeat.
- Cloth, hair, capes, accessories, and foliage may use bounded secondary motion.
- Root motion is presentation-only unless explicitly reconciled with
  authoritative movement.
- VFX are pooled, priority-limited, distance-limited, and reduced before
  telegraph clarity is compromised.

## Performance Contract

The proof must be measured at 1440x900 desktop and representative mobile
landscape viewports.

Targets:

- 60fps desktop within a 16.7ms frame budget;
- stable 30fps low-end mobile within a 33.3ms frame budget;
- no unbounded shader compilation or asset upload during combat;
- DPR capped by quality tier, with a maximum production cap of 2;
- frustum culling, distance culling, LOD, instancing, pooled VFX, and bounded
  dynamic lights;
- compressed textures and geometry are introduced only through a reviewed,
  browser-compatible asset pipeline;
- at most ten full-quality nearby player representations per Khu;
- distant entities use reduced animation update rates and simplified effects;
- React performs no per-frame gameplay state updates.

The first 3D PR must report frame time, draw calls, triangles, texture memory,
JavaScript bundle impact, and scene disposal behavior. Visual quality may not
hide unacceptable performance, and performance mode may not remove critical
telegraphs or other players.

## Security And Content Boundaries

- Published manifests select allow-listed models, materials, clips, sounds,
  VFX presets, NPC definitions, monster definitions, and map chunks.
- Remote JSON cannot inject scripts, shaders, URLs, routes, commands,
  permissions, or arbitrary component names.
- Admin publishing cannot mutate a running Khu. A Khu pins reviewed content and
  asset versions until a safe transfer or restart boundary.
- AI-generated drafts require human review, optimization, validation, and
  publication before runtime delivery.
- Storage and CDN credentials never enter React or Three.js.

## Migration Strategy

1. Preserve the Phaser proof and its tests while the 3D proof is isolated.
2. Introduce a renderer-neutral host and typed input/runtime contracts.
3. Prove one local 3D area with placeholder licensed or repository-owned assets.
4. Validate desktop and mobile frame budgets.
5. Migrate network snapshot/interpolation contracts without changing server
   authority.
6. Remove Phaser from the released world bundle only after parity tests pass.

No Phaser implementation is silently converted into Three.js code. Useful
behavior is migrated behind explicit interfaces and tested independently.

## Review Triggers

Revisit this decision if:

- measured browser/mobile targets cannot be met after bounded optimization;
- required accessibility cannot be delivered;
- production asset tooling introduces unsustainable ownership cost;
- a future native client requires a different renderer and has an approved
  compatibility plan.

## References

- [World Map Architecture](../WORLD_MAP_ARCHITECTURE.md)
- [Gameplay Input Architecture](../GAMEPLAY_INPUT_ARCHITECTURE.md)
- [Game Shell Architecture](../GAME_SHELL_ARCHITECTURE.md)
- [Visual Bible](../VISUAL_BIBLE.md)
- [Asset Pipeline](../ASSET_PIPELINE.md)
