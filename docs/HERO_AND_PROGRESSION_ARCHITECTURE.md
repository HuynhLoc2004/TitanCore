# TitanCore Hero And Progression Architecture

## Status And Scope

This document defines the owner-approved product direction for multiple heroes,
NPC-mediated progression, and future economy boundaries. It is architecture
only. It does not approve migrations, APIs, hero statistics, combat formulas,
skill trees, payment integration, shops, currencies, production content, or
Admin implementation.

## Player And Hero Identity

A TitanCore account may eventually own multiple playable heroes. Account
identity, visible player identity, and selected hero are separate concerns:

- the account owns authentication, sessions, entitlements, and player profile;
- the player profile owns the public display name;
- the selected hero owns gameplay presentation and hero-specific progression;
- generated internal usernames never become the hero or public identity.

The server is authoritative for hero ownership, selection, unlocks, progression,
equipment compatibility, and skill availability.

## Hero Roster

TitanCore is designed for a growing roster, not one permanent hero. Every
production hero requires:

- a distinct silhouette, personality, color accent, and narrative role;
- basic attack, dodge or movement action, passive, active skills, and a future
  ultimate where approved;
- coherent strengths, limitations, and cooperative value;
- complete idle, locomotion, attack, cast, impact, hit, downed, recovery, and
  victory presentation;
- unique but compatible VFX and audio language;
- mobile and desktop control mapping;
- portrait, lobby, Codex, world, result, and cosmetic consistency;
- localization and accessibility review.

Presentation archetypes such as Vanguard, Guardian, Engineer, Core Mage,
Ranger, and Support are useful design directions. They are not approved classes,
statistics, party requirements, or production hero names.

## Hero Codex

Before selecting or unlocking a hero, players must be able to understand it
without leaving the game:

- real animation preview rather than a static portrait alone;
- role, learning difficulty, and cooperative strengths;
- basic attack and skill previews;
- range, mobility, resilience, control, and support tendencies;
- concise descriptions plus optional detailed numbers;
- unlock and progression requirements;
- owned cosmetics and compatible equipment;
- desktop and touch-control preview.

Remote hero content references an allowlisted presentation schema. It cannot
inject executable code, arbitrary components, routes, HTML, CSS, permissions,
formulas, or server commands.

## Skill Progression

Skills are hero-specific and server-owned. A future skill definition may
contain:

- hero and skill identifiers;
- prerequisite hero/account level;
- prerequisite story or Mentor milestone;
- maximum rank;
- rank-specific cost and training requirement;
- server-owned combat definition reference;
- reviewed animation, VFX, audio, icon, and localized copy references.

Higher ranks may improve bounded effectiveness, cooldown, utility, or tactical
behavior. Cost growth must be understandable and capped. A purchased resource
cannot bypass account status, story prerequisites, maximum rank, or server
validation.

Exact damage, cooldown, scaling, targeting, prerequisites, rank count, and
respec rules require a dedicated Combat and Progression approval.

## NPC Roles

NPCs belong to a world's narrative and environment. They are animated world
characters, not merely floating shop buttons.

Potential registered roles include:

- `SKILL_MENTOR`: teaches or upgrades hero skills;
- `BLACKSMITH`: future crafting and equipment services;
- `MERCHANT`: approved world goods;
- `CORE_RESEARCHER`: story, discovery, and Core Shard context;
- `GEM_BROKER`: future premium-currency presentation;
- world-specific quest and story characters.

The role registry and security behavior are code-owned. Published content may
control reviewed appearance, dialogue, schedule, location, and allowed service
references. It cannot grant permissions, mutate balances, invent an endpoint,
or execute a payment.

NPC interaction keeps the Phaser world alive behind a bounded React or Phaser
overlay. Combat, transfer, disconnect, and account-state rules determine
whether interaction remains available.

## Proposed Resource Names

These names are approved product directions but do not approve schema or
economy implementation:

- `Core Insight`: gameplay-earned skill-learning resource;
- `Titan Gems`: premium currency acquired through a later approved payment
  system;
- `Gold`: common gameplay currency for ordinary services;
- `Core Shards`: rare narrative or crafting material, not direct premium cash.

Localization, iconography, balances, sinks, sources, caps, expiry, refunds, and
ledger behavior require the Game Economy phase.

## Fair Monetization Boundary

Recommended policy is bounded premium acceleration:

- free players can earn every gameplay-required skill and world unlock;
- Titan Gems may later purchase cosmetics, pets, emotes, reviewed passes, or
  bounded Mentor acceleration;
- premium spending cannot unlock exclusive combat power;
- premium spending cannot create uncapped permanent cooldown or damage
  advantage;
- no random paid outcome is introduced without legal, platform, probability,
  age-rating, and owner review;
- payment confirmation never happens in the realtime combat loop.

A possible future `Mentor Token` can shorten a bounded training wait or
supplement a capped amount of Core Insight. This is a proposal for the Economy
review, not approved behavior.

Direct unlimited exchange from Titan Gems to combat strength is not
recommended. It damages cooperation, ranking integrity, retention, and the
value of earned progression.

## Progression Loop

```text
hunt and explore
  -> earn level, materials, and Core Insight
  -> complete story or Mentor requirements
  -> learn or improve a hero skill
  -> qualify for harder world content
  -> unlock a World Gate
  -> retain access and revisit earlier worlds
```

Progression must avoid one mandatory low-probability drop blocking the main
story indefinitely. Recommended power and missing requirements are displayed
before a transfer or upgrade attempt.

## Transaction And Audit Boundary

Future skill learning, resource spending, inventory mutation, and immutable
ledger insertion must be designed as one durable PostgreSQL transaction.
Payment confirmation must use verified PayOS processing, idempotency, and the
transactional outbox defined by Phase 2.

Redis may cache safe progression reads but is not the durable balance or
entitlement authority. Phaser and WebSocket messages cannot mutate premium
currency directly.

Refund, chargeback, reconciliation, separation of duties, and Finance Admin
operations remain governed by `ADMIN_OPERATIONS.md`.

## Dynamic Content Boundary

Data-driven:

- hero presentation and published roster availability;
- registered skill presentation and rank copy;
- NPC appearance, dialogue, schedule, and approved service reference;
- world prerequisites and recommended power;
- reviewed item, reward, VFX, audio, and animation references.

Code-owned:

- ownership and authorization;
- combat formulas and legal state transitions;
- currency and ledger integrity;
- payment verification;
- unlock evaluation;
- safe registries and schema validation;
- anti-cheat, rate limits, and account status.

Publishing a new version affects future eligible sessions or map instances
according to a separately approved pinning policy. It never mutates an active
hero or balance silently.

## External Integration Approval Gates

Stop and request owner approval before:

- PayOS keys, webhooks, products, refunds, or production configuration;
- object-storage/CDN credentials for hero, NPC, skill, VFX, or audio assets;
- AI-provider configuration;
- licensed asset or audio purchases;
- production monitoring or analytics providers.

Only non-secret environment-variable names may be committed. Secret values are
configured by the owner in local ignored files, GitHub Environments, or a
production secret manager and are never printed or passed in URLs.

## Deferred Decisions

- initial production hero count and exact roster;
- hero acquisition rules;
- account versus hero level;
- skill tree shape, ranks, respec, and training duration;
- Core Insight, Gold, Titan Gems, and Core Shard schemas;
- exchange rates, caps, sinks, bundles, and regional pricing;
- NPC service APIs and Admin UI;
- quests, achievements, crafting, equipment power, and progression formulas;
- payment and mobile-store integration.

Each requires options, tradeoffs, recommendation, and explicit owner approval.
