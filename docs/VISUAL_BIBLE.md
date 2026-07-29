# TitanCore Visual Bible

## Status And Scope

This document defines the approved visual foundation for TitanCore, a premium
2D browser co-op boss game. It is a design contract, not approval to implement
UI, assets, audio, Phaser scenes, APIs, database migrations, or Admin tools.

The intended tone is colorful cartoon boss comedy: expressive, polished,
fast to understand, and funny without sacrificing combat readability.

## Art Direction

- Use bold silhouettes, clean forms, controlled detail, and expressive poses.
- Favor playful exaggeration and visual punchlines over realism.
- Meme influence belongs in reactions, timing, props, and animation, not
  copied third-party characters or short-lived UI jokes.
- Important game state must remain understandable without reading flavor text.
- Decorative detail must never compete with hazards, telegraphs, HP, or input.
- Published assets must share the same outline, lighting, perspective, and
  saturation rules. Mixing unrelated generated styles is not acceptable.

## Scene-First Experience Target

The owner-approved Raid Camp direction is scene-first. The illustrated world,
player hero, destination, and featured boss carry the primary experience.
React interface surfaces frame and clarify that world; they do not replace it
with a generic dashboard or a wall of decorative cards.

The approved quality references and practical asset breakdown are defined in
the [Integrated Creative Target Pack](CREATIVE_TARGET_PACK.md). Those images are
AI-assisted reference drafts, not runtime assets, final UI contracts, production
copy, or authorization to fabricate live data.

The visual target requires:

- one coherent foreground, middle-distance, and background composition;
- a featured boss visible before the primary raid decision;
- a player silhouette with stronger emphasis than secondary application chrome;
- compact support UI with real DOM text and controls;
- portrait-specific mobile composition rather than a compressed desktop scene;
- reviewed art variants that fit the published asset and performance budgets;
- visual continuity across lobby, loading, combat, result, and reward surfaces.

The historical Phase 4.2 lobby frontend commit is a technical prototype. Its
CSS geometry and placeholder composition do not define final product quality.
Reusable client behavior must be reviewed separately from its presentation.

## Color System

Core palette:

| Token | Value | Use |
| --- | --- | --- |
| `ink` | `#111329` | Primary dark surface and outline |
| `paper` | `#FFF8E7` | Warm light text and highlights |
| `coral` | `#FF5D73` | Damage, danger, urgent emphasis |
| `gold` | `#FFC857` | Rewards, celebration, primary highlight |
| `mint` | `#35D6A3` | Success, healing, ready state |
| `sky` | `#3EA7FF` | Information, friendly effects, links |
| `violet` | `#7556E8` | Magic, special actions, secondary accent |

Semantic colors must have stable meanings across React and the world renderer. Danger,
warning, success, information, rarity, and team identity must never depend on
color alone. Pair color with shape, icon, label, pattern, or animation.

Contrast must meet WCAG 2.2 AA for essential DOM text and controls. Combat
telegraphs require visual validation against every approved map palette.

## Typography

- Use a rounded, expressive display family for titles and a highly legible
  sans-serif family for UI, body text, numbers, and accessibility surfaces.
- Font selection and licensing require explicit asset approval.
- Runtime fonts are self-hosted, versioned, preloaded selectively, and served
  from the approved asset delivery domain.
- Never depend on a third-party font request during gameplay.
- Numeric combat text uses tabular figures where alignment matters.
- Do not scale font size directly with viewport width. Use bounded responsive
  sizes and container-aware wrapping.
- Display names, translated labels, and error text must be tested at their
  maximum approved lengths.

## Layout System

- Base grid: 4px.
- Spacing scale: 4, 8, 12, 16, 24, 32, and 48px.
- React lobby content width: maximum 1280px with responsive gutters.
- Component radii: 4-8px. Larger illustrated silhouettes may use custom shapes.
- Interactive controls use stable dimensions and visible focus treatment.
- Primary cards use a 2-3px outline or border and one clear elevation layer.
- Do not nest decorative cards inside cards.
- Page sections are unframed layout bands; cards represent individual room,
  item, reward, or actionable records.

## Icons

- React controls use one approved icon family with consistent stroke weight.
- Game items, skills, status effects, and rarity marks use reviewed atlas art.
- Do not use text-filled rounded rectangles where a standard icon is clearer.
- Unfamiliar icons require a tooltip and accessible name.
- Icons must remain recognizable at 20, 24, 32, and 48px display sizes.

## Character Language

- Player characters use approximately 2.5-head proportions.
- Hands, weapons, facial reactions, and action poses are intentionally large.
- Silhouettes must remain distinct when shown at combat scale.
- Team/player distinction uses a combination of accent color, marker shape,
  nameplate, and optional outline.
- Idle, movement, basic attack, skill, hit, down, revive, and celebration poses
  require consistent pivots and collision references.
- Cosmetics may alter approved visual slots but must not hide telegraphs,
  collision readability, or player identity.

## Boss Language

- Bosses occupy approximately three to six times the visual mass of a player.
- Each boss has one dominant silhouette and one memorable comedic premise.
- Weak points and phase changes are shown through shape, pose, color, sound,
  and animation, not text alone.
- Attack anticipation must be readable before spectacle begins.
- Boss artwork must define safe crop areas for lobby cards, loading screens,
  combat framing, and result screens.

## Map Language

Maps use three visual value layers:

1. Playable floor and movement boundaries.
2. Hazards, telegraphs, and interactive combat objects.
3. Non-interactive background and atmosphere.

The background must remain quieter than combat. Collision, safe areas, and
hazards must be readable on low-end displays and under reduced VFX quality.
Map definitions and art are published content, not React component constants.

## Item And Rarity Language

Rarity presentation combines:

- approved semantic color;
- frame geometry;
- corner or edge pattern;
- rarity icon;
- visible text label where space permits.

Color alone is insufficient. Item icons need a consistent camera angle,
lighting direction, transparent padding, and silhouette density. Inventory
thumbnails must not reuse low-resolution lobby thumbnails.

## Telegraph Rules

- Telegraphs have clear anticipation, active, and recovery states.
- Danger zones use edge shape, fill pattern, direction, and timing indicator.
- Telegraph opacity must remain legible over every approved map.
- Friendly and hostile effects must not share the same shape language.
- Reduced-motion mode may simplify animation but must preserve timing.
- No cosmetic or VFX layer may cover a critical telegraph.

## VFX And Animation

- Use short anticipation, strong impact, and fast visual decay.
- Prefer a few authored particles over unbounded emitters.
- Pool repeated projectiles, particles, and damage-number objects.
- Camera shake, chromatic effects, blur, and full-screen flashes are optional
  feedback layers and must have intensity caps.
- Animation timing follows gameplay state but does not establish authority.
- Hidden or sleeping scenes stop decorative animation and release listeners.
- Animation must be tested at 60fps and at the 30fps degraded-quality target.

## Audio

- Separate music, UI, combat impact, voice/comedy, and ambience buses.
- Support independent volume controls and full mute.
- Apply concurrency caps to repeated attacks and impact sounds.
- Duck music briefly for critical boss cues; do not bury telegraphs in music.
- Audio starts only after browser-compatible user interaction.
- Critical audio cues also require visual equivalents.
- Runtime delivery should prefer Opus/WebM with an approved fallback format.

## Accessibility

- Lobby and account experiences remain semantic DOM.
- Keyboard navigation, visible focus, screen-reader labels, and touch target
  sizing are mandatory.
- Reduced-motion mode disables nonessential camera movement, looping decoration,
  parallax, flashes, and excessive particles.
- Provide independent controls for shake, flashes, music, effects, and voice.
- Combat status changes require an accessible summary channel outside the
  high-frequency canvas update loop.
- A polished rotate-device state is required before landscape mobile combat.

## Resolution And Scaling

- Combat design space: 1280x720, 16:9.
- Mobile lobby supports portrait; mobile combat uses landscape.
- Preserve aspect ratio with a stable canvas container and letterboxing when
  required. Never stretch the canvas independently with CSS.
- Cap device pixel ratio at 2.
- Low-end quality may use a rendering resolution multiplier of 1-1.5 after
  profiling. Gameplay clarity must not be reduced blindly.
- Source sprites should support twice their intended maximum display size.
- Use consistent pivots, trim metadata, and transparent padding.
- Do not upscale raster art beyond its approved display size.
- Avoid fractional placement for UI art that requires crisp edges.

## Atlases And Compression

- Mobile-safe atlas maximum: 2048x2048.
- Desktop may use 4096x4096 only after runtime capability and memory checks.
- Split atlases by scene and lifecycle; do not build one global atlas.
- Use WebP or AVIF for suitable opaque illustrations.
- Use optimized PNG or lossless WebP where alpha-edge quality requires it.
- Keep lossless production masters outside runtime delivery.
- Every published asset variant records checksum, dimensions, format, byte size,
  and intended quality tier.

## Quality Tiers

| Capability | Low-end mobile | Standard mobile/desktop | High desktop |
| --- | --- | --- | --- |
| Target rate | 30fps stable | 60fps | 60fps |
| DPR cap | 1-1.5 | 2 | 2 |
| Particles | Low cap | Standard cap | Enhanced cap |
| Shadows/lights | Simplified | Standard | Enhanced if profiled |
| Animated players | Essential animation | Standard | Standard |
| Audio voices | Reduced concurrency | Standard | Standard |

Quality degradation order is decorative particles, optional shadows, background
animation, secondary VFX, and audio concurrency. Telegraphs, input response,
boss state, player state, and essential animation are never degraded away.

## Visual Acceptance Checklist

Validate at:

- 390x844 portrait lobby;
- 844x390 landscape combat and rotate transition;
- 1440x900 desktop;
- 1920x1080 desktop.

For every target:

- no horizontal overflow or clipped controls;
- no blurry primary art, stretched canvas, or incorrect crop;
- maximum display names and localized labels fit;
- loading, error, empty, reconnect, and degraded states are complete;
- critical text and controls meet contrast requirements;
- telegraphs remain visible against the map;
- reduced motion removes nonessential motion without hiding state;
- memory, frame time, texture count, and transferred bytes are recorded;
- screenshots are compared against approved visual references.

## Related Documents

- [Integrated Creative Target Pack](CREATIVE_TARGET_PACK.md)
- [Narrative Bible](NARRATIVE_BIBLE.md)
- [Audio Bible](AUDIO_BIBLE.md)
- [Game Shell Architecture](GAME_SHELL_ARCHITECTURE.md)
- [Asset Pipeline](ASSET_PIPELINE.md)
- [Phaser Renderer ADR](adr/ADR-PHASER-RENDERER.md)
- [Performance Rules](PERFORMANCE_RULES.md)
- [Security Rules](SECURITY_RULES.md)
