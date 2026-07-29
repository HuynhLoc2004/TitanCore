# TitanCore Gameplay Input Architecture

## Status And Scope

This document defines the approved cross-device input contract and animation
quality gates. It does not approve an input library, gameplay implementation,
specific character skills, combat balance, production assets, or audio.

## Unified Action Layer

Keyboard, mouse, touch, and future gamepad adapters emit the same actions:

```text
MOVE
AIM
ATTACK
SKILL_1
SKILL_2
SKILL_3
SKILL_4
DODGE
INTERACT
```

The approved world renderer consumes normalized actions. Network code sends
bounded intentions. Neither device adapters nor Three.js calculate trusted
damage, cooldown, loot, or movement legality.

Each action records:

- local input sequence;
- press, hold, release, or analog phase;
- normalized direction or aim when applicable;
- monotonic client time for presentation only;
- active control scheme;
- current scene/input generation.

Raw keys, pointer events, and touch coordinates do not cross the gameplay
boundary.

## Desktop Controls

Approved defaults:

| Action | Primary | Alternative |
| --- | --- | --- |
| Move | `WASD` | Arrow keys |
| Camera/aim | Mouse/pointer | Keyboard target assist |
| Basic attack | Left click | Approved keyboard fallback |
| Skills | `1`, `2`, `3`, `4` | Remappable later |
| Dodge/action | `Space` | Remappable later |
| Interact | Context key to be approved | Pointer interaction where safe |

Browser-reserved and accessibility-critical shortcuts must not be captured
globally. Input is active only while the game canvas has the expected gameplay
focus. Text chat suspends gameplay key capture without leaving movement stuck.

Keyboard-only play must remain viable through directional aim or target assist;
the exact assist algorithm requires combat approval and cannot choose an
off-screen or invalid target.

## Mobile Controls

Mobile landscape is the primary combat orientation:

- left-side 360-degree movement joystick;
- right-side camera-look region with bounded pitch;
- large basic-attack control on the right;
- three or four skill controls around the attack control;
- separate dodge control;
- minimum practical touch target 56px, with primary actions up to 72px;
- safe-area padding for notches and browser UI;
- pointer capture and multi-touch support;
- light target assist or lock with a clear target indicator.

Controls must not cover the local hero, boss telegraphs, critical health state,
loot companion, or system announcements. Layout responds to usable viewport
height, not only nominal screen dimensions.

The joystick has a bounded dead zone and normalized output. Losing pointer
capture, visibility, orientation, or focus emits a neutral/release state so the
hero cannot remain moving or attacking.

## Input Sampling

- Browser events update local input state.
- The gameplay loop samples that state at a stable bounded rate.
- Equivalent repeated states are coalesced.
- Edge actions such as skill press are retained until acknowledged by the local
  intention queue or safely expired.
- Network send frequency is independent from render FPS.
- Queue length is bounded and cleared on scene generation change, logout,
  disconnect, channel transfer, or reconciliation.

Exact rates require profiling. A 120Hz pointer or display must not generate
twice the authoritative action load of a 60Hz device.

## Responsiveness And Reconciliation

Input should feel immediate through local animation anticipation and, if later
approved, bounded movement prediction. The server remains authoritative.

- Start-up animation may begin locally after a legal intention is queued.
- Hit, damage, cooldown, reward, and boss reaction wait for authoritative facts.
- Rejection transitions to a reviewed recovery animation without snapping the
  whole scene unnecessarily.
- Reconciliation corrects position smoothly unless a security or geometry
  violation requires a hard correction.
- Late responses from a previous channel, scene, session, or auth generation
  are discarded.

Prediction details, hitboxes, targeting, telegraphs, and formulas are deferred
to the combat architecture phase.

## Animation Asset Contract

Review sheets communicate direction; runtime assets require production cleanup:

1. normalize transparent background and color profile;
2. separate alpha cleanly with no matte fringe;
3. use consistent frame bounds and stable foot/pivot anchors;
4. remove frame-to-frame scale and silhouette drift;
5. pack mobile-safe texture atlases;
6. verify skeletal animation in a real Three.js preview;
7. measure frame time and memory on desktop and landscape mobile.

A moving static cutout is not an approved character animation. Runtime character
and creature animation requires intentional frame sequences or an approved
skeletal workflow with:

- idle breathing and weight shift;
- locomotion with grounded contact;
- anticipation, action, impact, recovery, and cancel states;
- facing and pivot consistency;
- readable hit reactions;
- no accidental limb/weapon morphing;
- reduced-motion treatment for nonessential loops.

Bosses require stronger silhouette changes and telegraph timing than ordinary
monsters. Ordinary monsters may use smaller atlases but still require readable
idle, locomotion, attack, hit, and defeat states.

## Map Motion Contract

Map motion is layered and budgeted:

- distant parallax moves slowly;
- clouds, mist, water, flags, foliage, fire, and props use different periods;
- ambient loops avoid synchronized mechanical repetition;
- camera motion is bounded and never substitutes for character animation;
- gameplay collision does not move because of cosmetic animation;
- telegraphs always outrank ambient particles and foreground occlusion.

The quality system disables or reduces distant particles, secondary props,
shadows, and high-cost post effects before reducing entity or telegraph
readability.

## Skill And VFX Language

Future skills follow a consistent visual grammar:

```text
anticipation -> telegraph -> release -> impact -> recovery
```

- Friendly, hostile, and neutral effects are distinguishable by shape as well
  as color.
- Telegraph duration and hit timing come from server-approved combat data.
- VFX cannot hide hitboxes or imply an unconfirmed hit.
- Screen shake, flashes, chromatic effects, and camera displacement are capped.
- Repeated multiplayer effects are pooled and priority-limited.
- Reduced-motion replaces intense camera and particle motion with clear static
  cues.

Exact skills and numbers are not approved here.

## Audio Interaction Contract

Audio design is deferred to a separately approved production phase, but runtime
behavior must support:

- input confirmation without masking server-confirmed hit feedback;
- spatial or panned world ambience;
- priority and concurrency limits;
- separate music, ambience, SFX, voice, and UI controls;
- mobile unlock after user gesture;
- background-tab suspension;
- captions or visual equivalents for critical cues;
- reduced repetition through reviewed variations.

No external audio provider, license, asset, key, or dependency is approved by
this document.

## Accessibility

- Controls are remappable in a future approved settings phase.
- Essential actions have keyboard and touch paths.
- Color is never the only state cue.
- Critical audio has a visual equivalent.
- Touch controls expose accessible names and state where the platform permits.
- Motion, flash, camera shake, and vibration preferences are respected.
- Focus cannot become trapped in the canvas.
- Chat input and overlays restore the prior gameplay input state safely.

## Performance Gates

Targets inherit `GAME_SHELL_ARCHITECTURE.md`:

- 60fps target within 16.7ms;
- stable low-end fallback at 30fps within 33.3ms;
- low-end mobile runtime memory target 128MB;
- desktop runtime memory target 256MB;
- DPR cap 2;
- first room/map critical assets within approved mobile and desktop budgets.

Additional acceptance:

- no per-frame React state updates;
- no allocation-heavy input objects in hot loops;
- sprite, VFX, projectile, and audio instance pools are bounded;
- atlas dimensions respect tested mobile GPU limits;
- hidden scenes release or sleep input, audio, timers, and transient assets;
- 10 visible players plus monsters and one boss remain within frame budget;
- activity in the other 14 Khu does not reach the client render loop.

## Required Test Matrix

- desktop mouse plus `WASD`;
- arrow-key and keyboard-only targeting;
- touch multi-input: move plus attack/skill;
- pointer loss, blur, hidden tab, and orientation change;
- chat focus versus gameplay capture;
- scene disposal and stale generation;
- reduced motion;
- 390x844 class device in landscape-equivalent usable height;
- representative low-end Android GPU profile;
- 1440x900 desktop;
- 10 players, ordinary monsters, boss, telegraphs, loot companion, and bounded
  chat overlays;
- network jitter, reconnect, and authoritative correction.
