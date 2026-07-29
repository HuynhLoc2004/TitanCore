# TitanCore Asset Pipeline

## Status And Scope

This document defines the approved asset lifecycle and storage boundary.
[ADR: Provider-Neutral Asset Delivery](adr/ADR-ASSET-DELIVERY.md) records the
Phase 4.3 storage and delivery decision.

Implementation remains separated into reviewed pull requests. This document
does not itself add object-storage infrastructure, SDK dependencies,
migrations, APIs, Admin UI, asset production, AI providers, or CDN
configuration.

Images and audio are never stored as PostgreSQL binary data.

## Pipeline

```text
AI or human draft
  -> private quarantine
  -> human review
  -> optimization and variants
  -> immutable object upload
  -> metadata validation
  -> Admin preview
  -> publish asset manifest
  -> CDN delivery
  -> rollback to a prior manifest when required
```

AI output is always a draft. Validation or successful generation does not grant
publish permission.

## Storage Boundary

TitanCore uses a provider-neutral S3-compatible object-storage abstraction:

- MinIO for local development.
- Cloudflare R2 for production.
- CDN or approved public delivery domain in front of published assets.
- Separate private and published buckets.
- AWS SDK for Java 2.x behind the provider adapter for the first
  implementation.

Application domain code depends on an object-storage port, not provider-specific
classes. Provider adapters translate endpoint, region, path-style, signing, and
error behavior.

Required abstract operations are limited and capability-oriented:

- initiate or sign a bounded upload;
- inspect object metadata;
- read or stream an object for validation;
- publish or copy an approved immutable variant;
- generate bounded preview access;
- mark objects for retention-aware deletion.

Published Lobby and combat reads do not invoke these operations. Their URLs are
derived from trusted metadata and the configured public delivery base URI.

Bucket administration and broad list/delete permissions do not belong in
ordinary application runtime credentials.

## Storage Zones

### Draft And Quarantine

- Private by default.
- No public bucket access.
- Short-lived presigned access for approved file type and maximum byte size.
- Object keys are server-generated and not based on raw user filenames.
- Upload completion does not make an object publishable.
- Malware/content-safety scanning may be added before review.

### Published

- Contains optimized, reviewed, immutable variants only.
- Uses content-hashed object keys.
- Served with long-lived immutable cache headers.
- Public delivery uses the approved asset/CDN base URL, not storage credentials.
- Replacing an asset creates a new object and published dependency set.
- Is physically isolated from the private bucket.

Publication copies and verifies immutable candidates before committing the
PostgreSQL publication. A failed database transaction may leave an unreferenced
copy, but cannot leave committed content pointing at a missing critical object.
Cleanup handles unreferenced copies asynchronously after a reference check and
retention delay.

## Presigned Upload Rules

- The browser never receives storage access-key or secret-key credentials.
- Presigned operations are short-lived and scoped to one object key.
- The server allowlists method, MIME type, maximum size, and expected checksum.
- Completion verifies object existence and metadata before creating a reviewable
  asset record.
- Client-provided filenames, MIME types, dimensions, and checksums are untrusted
  until server-side validation.
- Presigned URLs must not be logged or persisted in audit metadata.

## Asset Metadata

Future metadata design must record:

| Field | Purpose |
| --- | --- |
| Asset ID | Durable application identity |
| Object key | Provider-neutral storage location |
| Content hash | Integrity and immutable identity |
| Media type | Validated MIME type |
| Byte size | Transfer and policy validation |
| Width/height | Raster or video dimensions |
| Duration | Audio/video duration |
| Variant | Master, mobile, desktop, thumbnail, atlas, audio format |
| Quality tier | Intended runtime capability |
| Review status | Quarantined, reviewing, approved, rejected |
| Publish status | Draft, published, archived |
| Source | Human, AI, licensed, internal |
| Version | Immutable asset version |
| Audit references | Author, reviewer, publisher, timestamps |

PostgreSQL stores this metadata and object key only. It does not store image,
audio, atlas, font, or video bytes.

## Validation

Before preview or publishing:

- compare uploaded bytes with the expected checksum;
- decode the file rather than trusting its extension;
- validate MIME, dimensions, duration, frame count, alpha, and byte size;
- reject decompression bombs and malformed media;
- enforce approved formats and per-asset-type limits;
- verify atlas metadata references valid frames and source textures;
- verify sprite pivots, trim data, and required animation frames;
- verify audio loudness, clipping, duration, and fallback format;
- record validation output without embedding binary or secrets.

Critical assets include player, boss, map, collision reference, telegraph, and
required UI readability assets. A manifest with a missing critical asset cannot
be published.

### Stylized 3D Runtime Assets

Released worlds use reviewed glTF/GLB assets and named skeletal animation clips.

```text
concept and silhouette review
  -> model and topology review
  -> UV and material authoring
  -> rig and socket validation
  -> animation and deformation review
  -> LOD and collision generation
  -> texture and geometry optimization
  -> gameplay-camera preview
  -> desktop and mobile performance review
  -> immutable upload and metadata
  -> human publication
```

- AI-generated images may guide concept exploration but are not runtime meshes,
  rigs, animation clips, materials, or proof of production quality.
- No workflow may convert an unrelated set of generated still poses into a
  released character animation.
- Models use consistent units, axes, origins, naming, skeleton conventions,
  sockets, bounds, and animation clip contracts.
- Runtime packages contain no editor history, source credentials, embedded
  external URLs, executable scripts, or unreviewed extensions.
- LODs, collision proxies, navigation proxies, light or vertex data, texture
  sizes, morph targets, skin influences, bones, triangles, materials, and draw
  calls are validated against tier-specific budgets.
- Texture or geometry compression requires reviewed browser decoder support,
  fallback behavior, licensing, and bundle measurements.
- Preview tooling shows neutral lighting, world lighting, orbit camera,
  animation, deformation, materials, LOD transitions, bounds, sockets, and
  mobile quality mode.

## Optimization

- Preserve an approved lossless master outside runtime delivery.
- Generate deterministic runtime variants from the reviewed master.
- Use WebP or AVIF for suitable opaque illustration.
- Use optimized PNG or lossless WebP for sensitive alpha edges.
- Use scene-scoped texture atlases with 2048x2048 mobile-safe limits.
- Produce audio formats according to the Visual Bible.
- Strip unnecessary metadata from published files.
- Never optimize by enlarging a low-resolution source.

Optimization output must be visually compared with the approved master at the
actual minimum and maximum display sizes.

## Preview And Human Approval

Preview renders the exact candidate manifest, variants, crop rules, and quality
tiers that would be published.

Human review verifies:

- style and intellectual-property policy;
- visual consistency and crop safety;
- combat readability and accessibility;
- technical validation and performance impact;
- AI provenance where applicable.

For the solo-owner MVP, the owner may author and publish with recent step-up
authentication. The action and approved version require immutable audit.

## Publishing And Manifests

A published dependency set is immutable and includes:

- content version references;
- asset IDs and exact immutable variants;
- checksums and delivery paths;
- required/optional classification;
- quality-tier mapping;
- created, approved, and published timestamps.

For the first vertical slice, `content_versions`, `content_version_assets`,
`asset_variants`, and `content_publications` form the manifest-equivalent
representation. A separate manifest table is not approved.

Battle-room creation pins the published content versions and resolved immutable
dependency set. Publishing or rollback never changes an active room. Rollback
creates a new publication that references a prior known-good set for future
rooms.

## Retention And Deletion

- Draft rejection may schedule deletion after an approved quarantine period.
- Published objects are not overwritten.
- An object cannot be deleted while referenced by a published manifest, active
  room, recoverable battle, audit requirement, or rollback window.
- Deletion is a reviewed lifecycle operation, not a direct Admin bucket command.
- Use mark, verify references, delay, and delete stages.
- Failed deletion remains visible and retryable.
- Storage lifecycle policies must not expire pinned runtime assets.

## CDN Delivery

- Use immutable URLs and long cache lifetimes for published objects.
- Manifests use shorter caching and explicit version identifiers.
- Configure CORS only for approved frontend origins and required methods.
- Do not allow arbitrary hotlinked drafts.
- Missing optional assets use an approved local fallback.
- Missing critical assets fail room loading safely.
- CDN or storage outage must not trigger database access per combat frame.

## Admin Lifecycle

Asset management follows:

```text
DRAFT -> QUARANTINED -> IN_REVIEW -> APPROVED -> PUBLISHED -> ARCHIVED
```

Required operations are preview, schedule, publish, archive, rollback,
version history, retention review, and immutable audit. Remote asset metadata
must never inject scripts, arbitrary URLs, routes, permissions, or components.

## Security

- Use least-privilege bucket-scoped credentials.
- Keep draft and published access policies separate.
- Never expose credentials to React or Phaser.
- Never log credentials, signed URLs, or private object metadata.
- Validate object keys and prevent path/key traversal assumptions.
- Restrict preview access to authenticated, authorized Admin users.
- Treat AI-generated media and metadata as untrusted input.

## External Configuration Timeline

The documentation phase defines names only and does not request values.

| Later phase | Expected environment references |
| --- | --- |
| Local storage adapter | `OBJECT_STORAGE_ENDPOINT`, `OBJECT_STORAGE_REGION`, `OBJECT_STORAGE_PRIVATE_BUCKET`, `OBJECT_STORAGE_PUBLIC_BUCKET`, `OBJECT_STORAGE_ACCESS_KEY`, `OBJECT_STORAGE_SECRET_KEY`, `OBJECT_STORAGE_PATH_STYLE` |
| Published delivery | `ASSET_PUBLIC_BASE_URL` |
| Production R2/CDN | Provider-neutral values above bound to approved R2 bucket and CDN configuration |
| AI/media generation | Provider credentials approved only in the separate generation phase |

Values remain outside source control. The Phase 4.3 documentation pull request
does not request, validate, or configure any key.

## Phase 4.3 Documentation Exclusions

- No MinIO or R2 deployment in the documentation pull request.
- No storage SDK or application adapter.
- No migration or asset table.
- No upload or delivery API.
- No Admin asset UI.
- No AI or audio provider.
- No generated or uploaded assets.

## Related Documents

- [Visual Bible](VISUAL_BIBLE.md)
- [Game Shell Architecture](GAME_SHELL_ARCHITECTURE.md)
- [Admin Operations](ADMIN_OPERATIONS.md)
- [AI Rules](AI_RULES.md)
- [Security Rules](SECURITY_RULES.md)
