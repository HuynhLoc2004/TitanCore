# ADR: Phaser 3 Combat Renderer

- Status: Accepted
- Scope: Phase 4 visual and game-shell architecture
- Decision owner: TitanCore Project Owner

## Context

TitanCore is a premium 2D browser co-op boss game. React already owns account
and application experiences. Combat requires scenes, cameras, input, sprite
animation, asset loading, synchronized audio, VFX, responsive scaling, and
simple movement/attack interactions.

The backend remains authoritative. The renderer consumes room state and sends
player intentions only.

## Options

### Phaser 3

Advantages:

- integrated scene lifecycle, cameras, input, tweens, animation, audio, loader,
  texture management, scaling, and optional physics;
- appropriate for a complete 2D game loop;
- existing TitanCore dependency and Phase 1 architectural direction;
- supports scene-local lifecycle and lazy combat initialization.

Disadvantages:

- larger and more opinionated than a rendering-only library;
- requires strict lifecycle discipline when embedded in React;
- optional systems can increase bundle and runtime cost if included blindly.

### PixiJS

Advantages:

- high-performance, flexible 2D rendering and scene graph;
- modular architecture and direct control over rendering systems;
- strong choice for visualization and custom engines.

Disadvantages:

- TitanCore would need to assemble or own more scene, input, audio, physics,
  transition, and game-lifecycle behavior;
- greater custom-engine surface for the first playable slice;
- does not improve the backend-authoritative boundary.

### DOM Or Canvas 2D Without A Game Framework

Advantages:

- minimal dependency surface for simple screens.

Disadvantages:

- unsuitable ownership cost for synchronized sprites, scenes, cameras, VFX,
  atlases, input, audio, and lifecycle management;
- higher risk of inconsistent timing and ad hoc engine behavior.

## Decision

Use Phaser 3 as TitanCore's combat renderer.

- React owns authentication, onboarding, lobby, inventory, settings, and future
  Admin UX.
- Phaser owns only combat rendering, input, scenes, cameras, animation, VFX,
  interpolation, and synchronized audio.
- Phaser is lazy-loaded at combat entry and mounted through a stable GameHost.
- Phaser does not own authentication tokens, authoritative state, rewards,
  room policy, or durable content publishing.
- Use WebGL as the production baseline.
- Use an appropriate supported fallback only after capability testing.
- Do not adopt WebGPU as the production baseline yet.
- Use lightweight Arcade Physics only if the approved movement slice benefits
  from it. Physics never becomes server authority.

## Consequences

Positive:

- the first playable can focus on room synchronization and combat feel instead
  of constructing a custom game framework;
- loader, scenes, textures, animation, input, audio, and scaling share one
  lifecycle;
- Phaser code remains isolated from React application features.

Costs and controls:

- Phaser must be route-split and absent from initial auth/lobby bundles;
- one GameHost instance is created per room entry and fully disposed on exit;
- scene and texture ownership requires memory/performance instrumentation;
- React and Phaser communicate through typed adapters, not shared mutable UI
  objects;
- frequently changed game content comes from immutable published versions and
  asset manifests, not scene constants;
- upgrades require visual, performance, lifecycle, and browser compatibility
  regression testing.

## Rejected Direction

PixiJS is not rejected as an incapable renderer. It is rejected for TitanCore's
current scope because adopting it would require additional custom game-engine
systems without a demonstrated product benefit.

## Review Triggers

Revisit this ADR only if:

- Phaser cannot meet measured mobile memory or frame-time targets after
  profiling and scoped optimization;
- required browser support changes materially;
- the approved game design no longer needs a game framework;
- a future renderer migration has a tested compatibility and content plan.

Renderer preference alone is not sufficient to reverse this decision.

## References

- [Game Shell Architecture](../GAME_SHELL_ARCHITECTURE.md)
- [Visual Bible](../VISUAL_BIBLE.md)
- [Asset Pipeline](../ASSET_PIPELINE.md)
- [Phaser Scene concepts](https://docs.phaser.io/phaser/concepts/scenes)
- [Phaser Loader concepts](https://docs.phaser.io/phaser/concepts/loader)
- [PixiJS architecture](https://pixijs.com/8.x/guides/concepts/architecture)
