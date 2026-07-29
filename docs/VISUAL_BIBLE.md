# TitanCore Visual Bible

## Status And Scope

This document defines the approved visual foundation for TitanCore, a premium
stylized 3D browser co-op action game. It is a design contract, not approval to
implement UI, assets, audio, renderer scenes, APIs, database migrations, or
Admin tools.

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

### 3D Character Quality Gate

TitanCore targets the polish and responsiveness expected of a modern stylized
third-person action RPG without copying another game's characters, costumes,
models, animations, world art, effects, audio, story, or interface.

Released characters are authored 3D assets, not flat AI images extruded into a
scene:

- clean topology and deformation loops support shoulders, elbows, wrists,
  hips, knees, ankles, face, hair, capes, and equipment;
- a reviewed humanoid rig uses consistent scale, axes, root, hips, feet, hands,
  weapon sockets, camera target, hit anchors, and VFX anchors;
- feet use contact-aware placement or reviewed inverse kinematics so slopes and
  steps do not produce skating, hovering, or terrain penetration;
- locomotion blends idle, start, acceleration, walk, run, sprint, strafe, turn,
  stop, jump, apex, fall, land, glide, hover, and recovery without abrupt pose
  popping;
- upper-body action layers may blend over locomotion without disconnecting the
  torso, weapon, hands, or facing;
- cloth, hair, ribbons, tails, capes, charms, and loose equipment use bounded
  secondary motion with stable collision and quality-tier fallbacks;
- faces support eye direction, blinking, brows, mouth shapes, emotional idles,
  combat effort, hit reaction, victory, and story expressions;
- silhouettes and expressions remain readable at the actual gameplay camera
  distance rather than only in close-up renders.

Plastic-looking output is rejected when it results from uniform roughness,
flat lighting, waxy skin, identical material response, excessive specular
highlights, missing contact shadows, or unrelated generated textures.

Material response remains stylized but physically coherent:

- skin, hair, cloth, leather, painted metal, polished metal, stone, foliage,
  magic, and translucent effects have distinct reviewed responses;
- toon ramps and controlled highlights preserve volume and facial readability;
- ambient, key, rim, contact, and environment lighting support the scene without
  washing every surface with the same highlight;
- outlines are selective and distance-aware rather than a thick uniform stroke
  around every object;
- color grading is world-specific, bounded, and never destroys skin tone,
  telegraph, rarity, or team readability.

Each hero requires a turntable, gameplay-distance review, animation review,
material review, mobile quality review, and silhouette comparison against the
approved roster before publication.

## Third-Person Camera Language

The released world never uses the current flat, front-facing side-view
composition as its primary gameplay camera.

The default exploration camera sits behind and above the local hero with a
slight lateral composition offset. It frames the path, destination, nearby
characters, terrain height, and sky rather than placing the hero as an oversized
cutout in the exact center of the screen.

- Continuous orbit yaw covers the full 360-degree range with no artificial
  front-facing stop.
- Bounded pitch lets the player inspect terrain below, landmarks above, aerial
  enemies, jumps, glides, and vertical routes without flipping the camera.
- Zoom supports reviewed exploration, combat, indoor, boss, and accessibility
  distances; it never becomes an unrestricted debug camera.
- Mouse drag or pointer-lock behavior is explicit on desktop. Touch uses a
  dedicated camera-look region separate from movement and skill controls.
- Camera-relative movement preserves intuitive forward, strafe, turn, jump, and
  aerial steering at every orbit angle.
- Camera collision uses a swept volume and soft recovery so walls, foliage,
  ceilings, large enemies, and props cannot trap the view or reveal unloaded
  space.
- Occluding foliage and approved props fade or simplify near the camera instead
  of hiding the hero or critical threats.
- Exploration uses smooth spring behavior and restrained look-ahead. Combat may
  tighten framing, bias toward a selected threat, and widen for boss telegraphs
  without stealing control.
- Jumping and falling preserve horizon and landing visibility. Gliding and
  aerial skills show altitude, direction, momentum, destination, and nearby
  threats rather than pointing the camera straight at the hero.
- Camera transitions have bounded acceleration and damping. Sudden snapping,
  constant auto-centering, excessive lag, mechanical orbit, and uncontrolled
  shake are rejected.

Camera composition is reviewed at minimum for:

1. exploration on open terrain;
2. narrow paths and interiors;
3. steep ascent and descent;
4. jump, fall, lift, glide, and aerial skill;
5. ordinary-monster groups;
6. large boss encounters;
7. ten-player Khu activity;
8. desktop 1440x900 and representative mobile landscape.

The player may recenter the camera with a clear action. Reduced-motion mode
retains manual orbit and gameplay visibility while reducing automated sway,
shake, acceleration, and cinematic displacement.

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

Stylized 3D maps require authored composition at player scale:

- terrain has readable paths, elevation, sight lines, landmarks, vertical
  routes, discoveries, and framing from the orbit camera;
- architecture, foliage, water, weather, creatures, NPC activity, particles,
  cloth, fire, and distant silhouettes provide layered ambient motion;
- the world must not resemble a static image wrapped around a flat arena;
- props and landmarks use material variation, contact, wear, color hierarchy,
  and scale cues rather than glossy uniform surfaces;
- camera rotation must retain attractive compositions and traversal clarity
  from every reachable direction, not only one promotional angle.

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
- Skills follow anticipation, release, travel, impact, reaction, and recovery.
- Aerial actions communicate lift, hang time, direction, momentum, cloth/hair
  response, landing weight, and authoritative movement limits.
- Multiplayer effect priority preserves the local hero, nearby threats,
  telegraphs, confirmed impacts, and boss state before decorative spectacle.
- Hit stop, camera impulse, controller vibration, sound transient, character
  reaction, particles, decals, and damage presentation are coordinated but
  individually bounded.

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
