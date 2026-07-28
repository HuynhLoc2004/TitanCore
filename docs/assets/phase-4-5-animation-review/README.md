# Phase 4.5 Animation Review Pack

## Status

This directory contains AI-assisted animation direction sheets for human
review. The sheets are not production atlases, published assets, combat
authority, hitboxes, damage definitions, or permission to ship generated
frames unchanged.

Approval means that silhouette, identity, action language, and timing beats may
advance to production cleanup. It does not bypass redraw, alpha extraction,
pivot alignment, atlas packing, checksum generation, optimization, in-engine
preview, accessibility review, or final publication.

## Review Inventory

| File | Grid | Direction under review |
| --- | --- | --- |
| `core-raider-basic-attack-review.png` | 4x2 | Idle, anticipation, active slash, follow-through, recovery |
| `rubber-duck-king-hammer-slam-review.png` | 4x2 | Crown-rattle anticipation, overhead telegraph, slam, squash, comedic recovery |
| `core-slash-vfx-review.png` | 4x3 | Core gather, slash, contact burst, crystal decay |
| `review-manifest.json` | N/A | Frame semantics, pivot policy, timing ranges, and runtime guardrails |

The chroma background intentionally remains in the review sources. These files
must never be imported by React or Phaser.

## Production Conversion Contract

An approved sheet advances through:

```text
owner visual approval
  -> frame consistency cleanup or authored redraw
  -> chroma removal and edge inspection
  -> fixed pivot and frame-bound normalization
  -> animation timing preview at 30 and 60 fps
  -> hitbox/telegraph design in a separate approved combat task
  -> mobile atlas packing and compression
  -> Phaser scene preview
  -> performance, readability, accessibility, and multiplayer review
  -> immutable asset publication
```

Production frames must preserve:

- one stable character or boss identity across every frame;
- one documented foot or ground pivot;
- full weapon, crown, Core, scarf, and silhouette readability;
- no costume, lighting, perspective, or scale drift;
- no frame-dependent collision authority;
- anticipation, active, and recovery phases that remain readable without
  relying on audio;
- VFX that cannot cover hostile telegraphs or another player's identity.

## Runtime Budgets

- Combat design space: 1280x720.
- Mobile combat orientation: landscape.
- Mobile atlas maximum: 2048x2048.
- Source art supports up to twice its intended display size.
- Essential character animation targets stable 30 fps on the LOW tier and 60
  fps on STANDARD/HIGH.
- Repeated effects use pooled sprites or emitters.
- Decorative particles degrade before attack, hit, and telegraph readability.
- Device pixel ratio remains capped by the Visual Bible.
- Review PNG byte size is not a runtime budget. Runtime variants must be
  measured after frame extraction and atlas packing.

## Owner Review

Approve, revise, or reject each sheet independently.

Check:

- identity continuity with the approved Raider and Rubber Duck King;
- readable anticipation before active motion;
- a clear impact silhouette;
- satisfying but short recovery;
- stable feet and plausible future pivot placement;
- no missing, duplicated, cropped, or changing equipment;
- boss comedy that does not obscure danger;
- Core VFX that reads as friendly/player-owned;
- readability at approximate combat scale;
- no copied franchise identity or accidental text.

## Explicit Exclusions

- No runtime sprite atlas or Phaser scene.
- No combat input, movement, damage, cooldown, hitbox, skill, or boss AI.
- No WebSocket, Redis, PostgreSQL, reward, room, or matchmaking change.
- No audio file, composer, provider, voice, key, or license approval.
- No Admin upload or publication implementation.
- No production asset is hardcoded into React.

Audio production remains the next separate approval boundary. Source,
licensing, provider, cost, and data handling must be reviewed before any audio
file or external credential is requested.
