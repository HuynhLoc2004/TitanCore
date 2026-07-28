# Phase 4.4 Raid Camp Asset Drafts

## Status

These files are AI-assisted review drafts. They are not approved runtime
assets, published content, object-storage objects, combat definitions, or a
license to bypass the human-review stage.

The Project Owner must approve or reject each direction before any asset is:

- integrated into the React Raid Camp;
- optimized as a runtime variant;
- uploaded to the published object-storage bucket;
- referenced by dynamic content;
- decomposed into animation, atlas, or combat assets.

## Draft Inventory

| File | Intended review | Runtime status |
| --- | --- | --- |
| `raid-camp-environment-desktop-draft.jpg` | Desktop scene, depth, portal destination, staging space | Draft only |
| `raid-camp-environment-mobile-draft.jpg` | Portrait-specific composition and safe overlay zones | Draft only |
| `rubber-duck-king-cutout-draft.png` | Lobby silhouette, personality, Core readability, crop safety | Draft only |
| `core-raider-cutout-draft.png` | First-player silhouette, identity continuity, crop safety | Draft only |
| `raid-camp-ui-vfx-sheet-draft.jpg` | UI material, semantic accents, focus/disabled direction, bounded VFX | Draft only |

The JPEG files are review derivatives. The cutout PNG files contain alpha and
are review derivatives. Lossless production masters, layered environment
sources, animation frames, atlases, and audio sources do not exist yet.

The combined review package is intentionally larger than the 1.5MB initial
Lobby runtime budget. These files must not be imported directly by React.
After approval, a separate implementation PR must produce measured responsive
WebP/AVIF or approved alpha variants, preload only critical files, and remain
within the transfer and decoded-memory budgets.

## Prompt Direction

The built-in image generation path used the approved creative-target images as
style and identity references.

Shared constraints:

- premium hand-painted 2D cartoon game art;
- bold silhouettes, clean dark outlines, controlled detail, and crisp edges;
- TitanCore ink, coral, gold, mint, sky, and violet semantics;
- no fabricated player counts, currencies, rewards, timers, or live state;
- no text, logo, watermark, arbitrary route, or executable UI;
- no copied third-party character or franchise identity;
- desktop and mobile environment compositions are independently authored;
- boss and hero subjects are isolated for later responsive composition;
- VFX remain bounded and secondary to gameplay readability.

## Owner Review Checklist

Review each draft for:

- immediate visual appeal and a recognizable TitanCore identity;
- continuity with `VISUAL_BIBLE.md` and `CREATIVE_TARGET_PACK.md`;
- a clear player silhouette and a memorable comedic boss silhouette;
- portal and destination readability within one minute of arrival;
- readable desktop composition at 1440x900;
- readable portrait composition at 390x844;
- no accidental text, fake data, generated username, or unsupported mechanic;
- no blurry edge, cropped weapon, green spill, or broken alpha;
- sufficient quiet space for semantic React controls;
- acceptable UI material, semantic colors, focus, disabled, and reduced-motion
  direction;
- approval, requested revision, or rejection recorded per file.

## Deferred Production Work

Approval of these drafts does not approve:

- final character names or gameplay classes;
- final boss mechanics, attacks, timings, damage, or rewards;
- runtime asset dimensions, compression, checksums, or object keys;
- layered parallax decomposition or sprite animation;
- final icons, fonts, audio, VFX atlases, or localization;
- hardcoded lobby content;
- Admin publishing, MinIO publication, R2/CDN provisioning, or production keys;
- React Lobby integration, room discovery, WebSocket, or Phaser combat.

After visual approval, the next implementation PR may create reviewed runtime
variants and integrate the React Raid Camp through the existing typed Lobby
Bootstrap contract. Dynamic content continues to select approved content and
asset references; React owns only the safe component and route registry.
