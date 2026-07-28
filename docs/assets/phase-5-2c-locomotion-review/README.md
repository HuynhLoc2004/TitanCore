# Phase 5.2C Core Raider Locomotion Review

## Status

`core-raider-locomotion-review.png` is an AI-assisted direction source.
`frontend/public/assets/locomotion/core-raider-locomotion-v1.png` is the
normalized alpha runtime proof derived from it.

Both remain `OWNER_REVIEW_REQUIRED`. They are not published dynamic content and
do not grant permission to treat generated imagery as final production content.

## Frame Contract

- Grid: 4 columns by 2 rows.
- Frame size: 444 by 444 pixels.
- Stable visual center: x 222 pixels.
- Stable ground line: y 412 pixels.
- Pivot policy: bottom-center ground.
- Sequences: idle, walk, run, and a visual-only dodge response.

The runtime collider remains independent from frame alpha. Frames do not define
damage, hit timing, collision authority, movement distance, or server state.

## Production Processing

1. Generate against the reviewed Core Raider identity.
2. Remove the flat chroma-key background and despill edges.
3. Normalize to an exact 1776 by 888 sheet.
4. Align every frame to the same center and ground line.
5. Validate transparent corners and inspect edges at native resolution.
6. Verify motion and frame time in Phaser on desktop and mobile landscape.

The source and runtime proof contain no third-party provider URL or credential.
