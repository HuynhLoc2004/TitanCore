# TitanCore Narrative Bible

## Status And Scope

This document defines TitanCore's owner-approved narrative foundation. It guides
future content design, visual storytelling, audio, localization, and LiveOps.
It does not approve quests, dialogue APIs, migrations, gameplay classes,
economy, production content seeds, or Admin implementation.

## One-Sentence Premise

When unstable Core Shards transform ordinary creatures and objects into
ridiculous Titans, the Core Raiders travel through connected worlds to contain
the chaos, recover the shards, and discover who is causing the fractures.

## Player Promise

The player is not a passive observer or a generic chosen hero. A Core Raider:

- enters dangerous but inviting worlds with friends;
- understands the immediate problem through the boss and environment;
- learns a boss's personality through readable behavior;
- wins through cooperation and observation;
- recovers a meaningful piece of the world's fractured Core;
- returns stronger and more curious about the larger mystery.

The story must support a short browser session. Players can enjoy one boss
without studying lore, while returning players discover connected clues.

## Tone

TitanCore combines sincere adventure with absurd escalation.

- Comedy comes from premise, pose, timing, sound, props, and reactions.
- Characters treat the mission seriously enough for success to matter.
- Bosses are memorable personalities, not disposable jokes.
- Defeat is playful and non-gory.
- Meme influence must remain original, translatable, and durable.
- Dialogue stays concise and never blocks the first minute of play.

Avoid:

- parody that requires knowledge of a third-party franchise;
- random humor with no world logic;
- cynical narration that makes progression feel meaningless;
- lore dumps before the player can act;
- jokes based on protected groups, harassment, or player identity.

## World Foundation

### The TitanCore

The TitanCore is an ancient network that stabilizes routes between many small
worlds. It is a system, a place, and a mystery rather than a player currency.
Its exact origin remains unrevealed during the first slice.

### Core Shards

A Fracture can release unstable Core Shards. A shard amplifies the strongest
trait of whatever absorbs it. This creates a repeatable narrative rule:

- a rubber duck's confidence becomes royal tyranny;
- a refrigerator's hunger for cold becomes a frozen fortress;
- a sneaker-wearing shark's speed becomes a coastal disaster;
- a ramen bowl's steam becomes a flying storm.

This rule allows funny bosses while preserving a coherent world.

### The Raid Camp

The Raid Camp is a safe hub built around a controlled Core Gate. It provides:

- a visible destination and featured threat;
- party assembly and preparation;
- evidence of recovered worlds and returning characters;
- story changes through approved banners, props, ambience, and announcements;
- a natural transition from React lobby to Phaser combat.

The camp evolves through published content, not hardcoded page decoration.

## Long-Arc Mystery

The first chapter establishes that Fractures are becoming too precise to be
accidental. Someone is learning to direct shards toward emotionally and
visually potent targets.

The long arc asks:

1. Why is the TitanCore destabilizing?
2. Who benefits when worlds become isolated?
3. Why do some Titans protect their shard rather than consume it?
4. Can every transformed boss safely return to normal?
5. What responsibility do the Core Raiders have after the battle?

Answers are released gradually through bosses, environments, results, and
optional lore. No first-slice feature depends on resolving the mystery.

## First Slice: Rubber Duck King

### Premise

A ceremonial bathhouse duck absorbs a shard during a town festival. Its need
to be the center of attention expands into a floating kingdom where every bell,
banner, and puddle declares it royal.

### Personality

- proud, theatrical, and easily startled;
- uses a patched squeaky hammer as a royal weapon;
- pauses to admire its own crown;
- becomes dramatically offended when the Core weak point is exposed;
- deflates into a harmless, embarrassed form on defeat.

### Narrative Readability

The player understands the encounter without dialogue:

- the crown communicates self-appointed royalty;
- the hammer communicates impact;
- the bright Core crystal communicates the objective;
- circular splash shapes communicate area danger;
- squeaks and reactions communicate comedy;
- repaired festival decorations communicate the result.

Exact attacks and mechanics remain deferred to combat design.

### Emotional Beat

```text
curiosity
  -> surprise
  -> confident preparation
  -> readable danger
  -> cooperative breakthrough
  -> comic release
  -> warm reward
```

## Core Raider Presentation Archetypes

The initial visual lineup contains four presentation archetypes:

- **Vanguard:** bold, energetic, direct silhouette.
- **Engineer:** inventive, optimistic, prop-driven comedy.
- **Guardian:** calm, dependable, physically grounded.
- **Mage:** curious, expressive, linked visually to Core phenomena.

These labels organize art and dialogue contrast. They do not define approved
classes, abilities, statistics, or required party composition.

Every playable identity needs:

- a clear silhouette and color accent;
- one readable motivation;
- a short reaction vocabulary;
- consistent treatment across portrait, lobby, combat, result, and cosmetics;
- names and dialogue that pass localization and cultural review.

## Story Delivery Hierarchy

Story is delivered in this order:

1. Environment and boss silhouette.
2. Animation and audio behavior.
3. Objective and result presentation.
4. Short dialogue, announcement, or loading line.
5. Optional lore and collection detail.

Critical objectives cannot depend on flavor text. Skipping optional story never
prevents play.

## Dynamic Content Boundary

Admin-managed narrative content may include:

- localized boss introductions;
- event and chapter copy;
- approved dialogue and loading tips;
- environmental story variants;
- published lore entries;
- schedule and audience selection;
- references to reviewed character, boss, map, and audio assets.

Code owns:

- safe schemas and length limits;
- component and cue registries;
- permissions and account state;
- route behavior;
- combat legality and reward rules;
- accessibility fallbacks.

Narrative content cannot inject scripts, HTML, routes, commands, permissions,
external redirects, combat formulas, or active-room mutations.

## Localization And Accessibility

- Source text uses plain, translatable sentences and avoids wordplay as the only
  carrier of meaning.
- Vietnamese and English are reviewed at maximum UI lengths.
- Essential boss cues have visual and audio equivalents.
- Subtitles identify speakers and important non-speech cues.
- Fast dialogue can be replayed in an accessible bounded history.
- Humor cannot depend only on sound, color, or rapid motion.

## Narrative Acceptance

A boss concept is not ready when it has only an appearance. It must pass:

- one-sentence premise;
- recognizable silhouette;
- personality expressed without lore text;
- readable danger and weak-point language;
- original comedic hook;
- defeat and recovery treatment;
- audio motif opportunity;
- localization and cultural review;
- connection to the TitanCore mystery;
- a reason for players to care about the outcome.

## Deferred Narrative Work

- final names and biographies for Core Raiders;
- chapter count and season structure;
- quest, achievement, and collection schemas;
- dialogue system and localization administration;
- economy and progression story;
- additional bosses and maps;
- final villain and TitanCore origin.

## Related Documents

- [Creative Target Pack](CREATIVE_TARGET_PACK.md)
- [Visual Bible](VISUAL_BIBLE.md)
- [Audio Bible](AUDIO_BIBLE.md)
- [Admin Operations](ADMIN_OPERATIONS.md)
