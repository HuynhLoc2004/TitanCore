# TitanCore Living World Presentation

## Status And Scope

This document defines the owner-approved visual goal for living cinematic maps.
It does not approve production assets, animation software, shaders,
dependencies, audio providers, licenses, gameplay code, or infrastructure.

A TitanCore map should feel like a gentle animated film that remains responsive
to players. It must not be a static background with one global pan, zoom, or
bobbing transform.

## Layered Scene Model

A world scene is composed from independently controlled layers:

1. sky, celestial light, and distant weather;
2. far silhouettes, islands, mountains, or architecture;
3. midground structures, trees, bridges, waterfalls, and machines;
4. authoritative navigation and collision ground;
5. players, NPCs, ordinary monsters, elites, bosses, drops, and gates;
6. gameplay VFX, telegraphs, and interactions;
7. foreground foliage, mist, debris, and occlusion;
8. UI and accessibility presentation outside world depth.

Each layer has a stable coordinate and scale contract. Parallax never changes
authoritative collision or makes gameplay targets visually detach from their
positions.

## Ambient Motion

Reviewed world-specific motion may include:

- clouds drifting at varied speeds;
- flags, ropes, grass, trees, and clothing responding to wind;
- fire, smoke, sparks, lanterns, and light flicker;
- water flow, waves, foam, falls, reflections, and ripples;
- moving cloud shadows and soft light shafts;
- distant creatures, vehicles, machinery, or settlement activity;
- NPC work, rest, conversation, and player reactions;
- sleeping, feeding, patrolling, or social monster behavior;
- an active Core Gate with controlled energy and world-state response.

Loops use varied periods and seeded phase offsets. A whole scene must not pulse
or reverse at the same moment. Slow motion remains subtle enough that players
can read movement, enemies, telegraphs, and controls immediately.

## World State Presentation

A world may transition through:

```text
ordinary hunting
  -> boss warning
  -> boss arrival
  -> active encounter
  -> boss defeat or departure
  -> world recovery
```

State can influence light, weather, ambience, music layers, gates, NPC
reactions, distant effects, and navigation guidance. It cannot move collision,
invent boss facts, or hide the ordinary hunting experience in other Khu.

Boss state is server-authoritative. The client plays a pinned, reviewed
presentation for the received state.

## Animated Entities

### Heroes

- breathing, weight shift, gaze, and personality idle;
- grounded locomotion and direction changes;
- anticipation, attack/cast, impact, recovery, and cancel;
- hit, downed, revived, victory, NPC interaction, and gate travel;
- equipment and cosmetic attachment stability.

### Monsters

- habitat idle and social behavior;
- patrol, alert, chase, attack, hit, defeat, and respawn presentation;
- clear silhouettes and threat roles;
- bounded variations that do not disguise hitbox or behavior.

### Bosses

- entrance and phase transition spectacle;
- readable telegraph, action, impact, vulnerability, and recovery;
- environmental and audio reaction;
- defeat that resolves the story beat without gore.

### NPCs And Companion

- occupation and personality loops;
- proximity awareness and interaction cue;
- dialogue and service presentation without freezing the world unnecessarily;
- loot companion arrival only after confirmed grants.

A moving or scaling static cutout is not final entity animation.

## Camera

- smooth follow with bounded damping;
- small look-ahead in the movement direction;
- controlled framing for gates, NPCs, events, and boss arrival;
- brief capped impact shake;
- no permanent sway, zoom pulsing, or forced camera motion;
- instant accessibility override for reduced motion.

Camera motion cannot obscure touch controls or cause the local hero to leave a
safe gameplay region.

## VFX And Readability

Priority order:

1. hostile telegraphs and lethal state;
2. local hero and immediate interaction;
3. boss and monster action;
4. confirmed hit and reward feedback;
5. ambient world motion.

Ambient particles and foreground layers yield before gameplay information.
Friendly, hostile, and neutral effects differ by shape, timing, motion, and
sound, not color alone.

Repeated effects are pooled, capped, and distance-prioritized. Off-camera or
other-Khu effects are not rendered.

## Audio Landscape

Each world eventually requires:

- music identity and adaptive layers;
- spatial ambience by biome/area;
- surface-aware footsteps;
- world props and gate sounds;
- hero, monster, boss, NPC, UI, skill, hit, and reward families;
- concurrency and priority limits;
- reviewed variations to avoid repetition;
- captions or visual alternatives for critical cues.

No audio provider or asset is approved here. Before audio production, present
source, license, cost, file format, loudness target, implementation plan, and
required non-secret configuration. Stop before requesting any key.

## Asset Construction

- Backgrounds are separated into useful depth and animation layers.
- Runtime sprites use cleaned alpha, stable pivots, consistent frame bounds,
  and tested atlases.
- Vector or skeletal animation is used only when approved and visually suitable.
- Video is not the default gameplay background; it is difficult to adapt,
  synchronize, quality-scale, and make interactive.
- Critical world layers have reviewed fallback assets.
- Object storage contains immutable optimized assets; PostgreSQL contains only
  metadata and object keys.

AI output is a draft. Human review checks anatomy, consistency, copyright risk,
alpha, frame continuity, VFX readability, compression, and suitability before
publication.

## Performance Tiers

### High

- complete ambient layer set;
- higher particle and reflection budget;
- richer shadows and secondary NPC/prop motion.

### Balanced

- complete gameplay animation;
- reduced distant activity and particles;
- simplified reflections and secondary lighting.

### Low

- all critical hero, monster, boss, telegraph, gate, and interaction motion;
- lower update rate for distant ambience;
- static reviewed fallback for selected nonessential layers;
- no costly post-processing.

Quality never removes gameplay animation or telegraph clarity merely to retain
decorative motion.

## Runtime Budgets

In addition to `GAME_SHELL_ARCHITECTURE.md`:

- only the current Khu is fully rendered and synchronized;
- animate nearby world props at full rate and distant props at a reduced rate;
- cull off-camera entities and particles conservatively;
- pool common VFX, labels, projectiles, and audio instances;
- cap device pixel ratio at 2;
- sleep scene systems in hidden tabs;
- release world-specific atlases and audio after safe transfer;
- preload only the destination's critical pinned manifest;
- stream optional ambience after gameplay is ready;
- preserve 60fps target and stable 30fps low-end fallback with 10 players,
  ordinary monsters, and approved encounter effects.

Every map ships with measured texture memory, decoded audio memory, draw calls,
entity count, particles, and frame-time percentiles on representative devices.

## Responsive And Accessibility Rules

- mobile combat is landscape-first with safe areas and touch-control clearance;
- world text and interaction labels fit short-height viewports;
- camera framing accounts for virtual controls;
- reduced motion removes shake, large parallax, rapid particles, and intense
  transitions while preserving a gently living scene;
- photosensitive-safe mode limits flashes and rapid luminance changes;
- critical events use shape, text, and audio alternatives;
- pausing or opening accessibility settings never leaves a held input active.

## Visual Acceptance

A map is not ready unless:

- it looks coherent in both a screenshot and continuous motion;
- there is no single static image pretending to be the whole world;
- loops have no obvious seams or synchronized reversal;
- sprites stay sharp at tested DPR and camera scales;
- hero, monster, NPC, boss, item, and gate silhouettes remain readable;
- ambient movement never hides telegraphs;
- 390x844-class mobile landscape and 1440x900 desktop framing pass;
- reduced-motion remains attractive and understandable;
- low-end quality passes frame and memory budgets;
- asset source, license, review, optimization, and publication history exist.
