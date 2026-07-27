# TitanCore Audio Bible

## Status And Scope

This document defines the owner-approved audio direction for TitanCore. Audio
must be memorable, readable, funny, performant, and safe for browser playback.
It does not approve audio files, composers, voice actors, generation providers,
dependencies, storage configuration, APIs, or implementation.

No temporary sound may silently become production audio. AI-generated audio is
draft material and requires human review before optimization and publishing.

## Sonic Promise

TitanCore should sound like a confident cartoon adventure built from tactile
objects, expressive characters, and powerful Core energy.

The sonic identity combines:

- warm, playful acoustic rhythm;
- chunky physical impacts;
- crystalline Core tones;
- short character reactions;
- boss-specific comedic materials;
- clear telegraph cues that remain readable under music.

Audio supports player decisions first and spectacle second.

## Audio Buses

| Bus | Content | Player control |
| --- | --- | --- |
| Master | Final output | Volume and mute |
| Music | Lobby and combat music | Independent volume |
| Ambience | Wind, camp, portals, map environment | Independent volume |
| UI | Focus, press, confirmation, error | Independent volume |
| Combat | Attacks, impacts, projectiles, telegraphs | Independent volume |
| Character | Efforts, reactions, celebration | Independent volume |
| Boss | Voice, material identity, phase cues | Independent volume |

Master mute is always available. Settings persist through the approved player
settings contract when implemented.

## Browser Playback Rules

- Audio begins only after an eligible user interaction.
- Opening login, onboarding, or lobby must not unexpectedly autoplay sound.
- A clear sound control is available before sustained playback.
- Background tabs suspend nonessential audio and resume without overlapping
  duplicate loops.
- Reconnect, scene restart, and route exit dispose or reuse sources safely.
- Audio context failure does not block authentication, lobby, or combat.

## Music Direction

### Raid Camp

The camp theme is adventurous and welcoming rather than grandiose:

- playful plucked rhythm;
- warm percussion;
- a short ascending Core motif;
- light brass or reed character;
- enough space for ambience and UI.

The loop should tolerate long lobby stays without fatigue. Optional stems may
add energy when a party becomes ready, but readiness data must be real.

### Combat

Combat music prioritizes pulse and telegraph space:

- bounded intensity layers;
- no constant maximum-density arrangement;
- brief ducking for critical boss cues;
- victory transition aligned with durable completion;
- reconnect state avoids restarting the entire track unnecessarily.

Dynamic music responds to approved battle state but never determines it.

## Core Motif

Core energy uses a stable family of glassy, resonant tones:

- clean ascending interval for safe activation;
- unstable detuned shimmer for a Fracture;
- short descending fracture for exposed weakness;
- resolved warm chord for recovery and reward.

The motif remains recognizable across UI, portal, boss, reward, and story
contexts without reusing one identical sound everywhere.

## Rubber Duck King Sound Identity

The first boss combines:

- pitched rubber squeaks for personality;
- padded wooden-barrel impacts for the hammer;
- water slaps and bubble pops for area attacks;
- crown rattles for anticipation;
- crystalline Core resonance for the weak point;
- an exaggerated deflation release for defeat.

Comedy cannot obscure danger. Telegraph cues use a distinct onset and frequency
shape before the funny impact layer begins.

The concept audio sequence is:

```text
crown rattle
  -> short royal squeak
  -> readable hammer or splash warning
  -> impact
  -> brief recovery reaction
```

Exact timings and attack mappings remain deferred to combat design.

## Cue Language

Every combat cue is classified:

| Class | Purpose | Priority |
| --- | --- | --- |
| Critical telegraph | Warn before unavoidable-risk action | Never dropped |
| Player confirmation | Confirm accepted input/state | High |
| Impact | Communicate contact and strength | High with concurrency cap |
| Character reaction | Express hit, down, revive, victory | Medium |
| Boss personality | Communicate phase and comedy | Medium/high |
| Decorative ambience | Give place and mood | First to reduce |

Critical cues also require visual equivalents. Audio-only gameplay information
is prohibited.

## UI Audio

- Focus sounds are subtle and disabled for high-frequency pointer movement.
- Press and confirmation sounds are short and distinct.
- Errors use a calm, non-punishing cue.
- Reward reveal uses staged anticipation and resolution.
- Disabled controls do not play success sounds.
- Repeated list navigation has a strict voice/concurrency limit.

UI sounds must not resemble combat danger cues.

## Voice And Comedy

The MVP may use short nonverbal reactions rather than full voice acting.

- Keep vocal bursts concise and localizable through subtitles or labels.
- Avoid repetition that becomes irritating during farming.
- Apply per-character and global concurrency caps.
- Never use generated celebrity likeness or unlicensed voice identity.
- Human review covers pronunciation, cultural context, and age suitability.

## Technical Delivery Targets

Preferred runtime delivery:

- Opus in WebM or Ogg where supported;
- one approved fallback format based on browser validation;
- lossless masters retained outside runtime delivery;
- music and long ambience streamed or progressively loaded;
- short repeated cues decoded and pooled;
- scene and boss audio grouped by immutable manifest;
- loudness normalized during production;
- clipping, silence, malformed duration, and metadata validated.

Initial targets for profiling:

| Concern | Target |
| --- | --- |
| Lobby audio initial transfer | At most 800KB after interaction |
| First-room audio transfer | At most 1.5MB mobile, 3MB desktop |
| Simultaneous ordinary voices | 12 LOW, 24 STANDARD |
| Identical rapid impact voices | At most 3 before replacement/stealing |
| Hidden-tab decorative voices | 0 |
| Critical telegraph reserve | At least 1 protected voice path |

These are review targets, not permission to degrade required cues. Final values
must be measured on approved browsers and devices.

## Mixing And Safety

- Use a consistent loudness target across music, effects, and voice.
- Cap peak output and validate with headphones and device speakers.
- Avoid prolonged high-frequency energy and startling volume jumps.
- Camera shake and full-screen flashes are never required to understand audio.
- Music ducking is short and bounded.
- Multiple players attacking simultaneously must not sum identical impacts
  without voice limiting.

## Accessibility

- Subtitles cover speech and important non-speech events.
- Critical telegraphs have visible anticipation and active states.
- Independent bus controls and full mute are required.
- Mono playback must preserve critical cues.
- Important direction cannot depend solely on stereo position.
- Reduced-motion does not disable necessary audio automatically; shake, flash,
  motion, and audio controls remain independent.

## Review And Publishing

Audio follows:

```text
human or AI draft
  -> private quarantine
  -> narrative and gameplay review
  -> edit and mix
  -> loudness, clipping, duration, and format validation
  -> in-context preview
  -> human approval
  -> immutable variants and manifest
  -> published delivery
```

Review confirms originality, licensing, cue readability, repetition tolerance,
performance, accessibility, and consistency with the Visual and Narrative
Bibles.

## First Vertical-Slice Deliverables

- one Raid Camp loop;
- one camp ambience bed;
- one Core portal activation sequence;
- one Rubber Duck King musical identity;
- boss reveal, telegraph, impact, weak-point, stun, and defeat cues;
- basic attack, one skill, hit, down/revive if approved, and victory cues;
- essential UI focus, press, error, party-ready, and reward cues;
- subtitle/cue labels and silent/reduced test modes.

No file is produced until asset delivery, source/licensing, and human review are
approved.

## External Configuration

No key or provider configuration is required for this documentation phase.
Composer, sound-library, voice, AI, and generation-provider decisions are
deferred. Provider credentials must not be requested before separate cost,
licensing, security, and data-handling approval.

## Related Documents

- [Creative Target Pack](CREATIVE_TARGET_PACK.md)
- [Narrative Bible](NARRATIVE_BIBLE.md)
- [Visual Bible](VISUAL_BIBLE.md)
- [Asset Pipeline](ASSET_PIPELINE.md)
