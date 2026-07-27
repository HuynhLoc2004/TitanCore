# ADR: Provider-Neutral Asset Delivery

## Status

Accepted for Phase 4.3 documentation on 2026-07-28.

Implementation remains subject to its own reviewed pull requests. This decision
does not add an object-storage SDK, Docker service, application configuration,
API, migration, Admin UI, production asset, or external credential.

## Context

TitanCore must deliver reviewed image, atlas, font, and audio assets without:

- storing binary media in PostgreSQL;
- coupling domain code to MinIO, Cloudflare R2, or another provider;
- routing normal game downloads through the Spring Boot process;
- exposing draft or rejected media publicly;
- allowing an asset replacement to change an active room;
- making Lobby or combat rendering depend on a live storage API call.

The Phase 4.2 schema already provides:

- `asset_objects` for immutable source metadata and review state;
- `asset_variants` for immutable runtime variants;
- `content_version_assets` for explicit content dependencies;
- `content_versions` and `content_publications` for immutable published content.

The Lobby bootstrap contract already exposes an optional `deliveryUrl`. It is
currently `null` because the delivery boundary has not been implemented.

The initial product target is 100-200 concurrent users, with eight players in
the first room. The selected design must remain proportional to that target.

## Decision

TitanCore will use a provider-neutral S3-compatible storage port with:

- MinIO as the local implementation;
- Cloudflare R2 or another approved S3-compatible implementation in production;
- one private bucket for drafts, quarantine, validation, and preview;
- one published bucket for reviewed immutable runtime variants;
- a public delivery domain or CDN in front of the published bucket;
- short-lived presigned operations only for bounded private upload or preview;
- direct immutable delivery for published runtime assets.

Backend domain code depends on capability-oriented ports. Provider adapters own
endpoint, region, path-style addressing, credentials, signing, and provider
error translation.

The first adapter will use AWS SDK for Java 2.x S3 APIs behind those ports.
MinIO-specific classes must not cross the adapter boundary.

## Alternatives

### Backend Proxy For Runtime Assets

Rejected as the default delivery path.

It centralizes access control, but consumes application bandwidth and threads,
adds latency, reduces CDN effectiveness, and makes the backend a bottleneck.
Backend streaming remains available only for tightly controlled Admin
validation or preview operations when direct presigned access is unsuitable.

### Presigned URLs For All Runtime Assets

Rejected for published gameplay assets.

Expiring URLs reduce cache stability, complicate immutable manifests, and can
fail during a room. Presigned URLs are bearer capabilities and remain limited
to private draft upload and preview.

### MinIO Java SDK As The Domain Client

Rejected as the initial adapter choice.

It can operate against S3-compatible providers, but exposes a provider-branded
API. The standard S3 adapter gives TitanCore a narrower production portability
boundary. A future adapter may use another SDK without changing domain code.

## Storage Zones

### Private Bucket

The private bucket contains:

- uploaded originals;
- AI-assisted or human drafts;
- quarantine objects;
- validation inputs;
- optimized candidates awaiting approval;
- Admin preview candidates.

Rules:

- no public read policy or public delivery domain;
- server-generated immutable keys;
- short-lived, single-object presigned operations;
- method, content type, byte limit, and expected checksum are bound where the
  provider supports them;
- signed URLs and credentials are never logged or stored in PostgreSQL;
- upload completion never implies approval or publication.

### Published Bucket

The published bucket contains only reviewed runtime variants copied from the
private bucket.

Rules:

- object keys include a cryptographic content hash;
- an existing object is never overwritten;
- delivery is read-only through the approved public base URL;
- published objects receive long-lived immutable cache headers;
- bucket listing, write, and delete are unavailable to browsers;
- storage lifecycle rules cannot expire referenced or retained assets.

The same provider-neutral `object_key` may identify the candidate in the
private bucket and its verified copy in the published bucket. Bucket selection
is an operation boundary, not client-controlled data and not an injected URL.

## Object Key Contract

The backend generates keys. Raw filenames, usernames, content labels, URLs, and
client paths do not determine storage locations.

An implementation must use a bounded canonical shape such as:

```text
assets/{media-category}/{sha256-prefix}/{sha256}/{variant}.{extension}
```

Requirements:

- lowercase content hash;
- allowlisted extension derived from decoded media;
- no traversal segment, empty segment, backslash, query, fragment, or encoded
  separator;
- no provider name, bucket name, credential, email address, or player identity;
- the same bytes and variant definition produce the same immutable identity.

The exact formatter is code-owned and tested. Admin content can select an
approved asset identity but cannot supply an arbitrary object key or URL.

## Publish Consistency

Object storage and PostgreSQL do not share a transaction. Publication therefore
uses prepare-then-commit:

1. Load the immutable content version and explicit asset dependencies.
2. Lock the relevant publication and asset workflow records using the approved
   database ordering.
3. Require every critical dependency to be approved and every selected variant
   to satisfy its technical and performance policy.
4. Copy missing immutable objects from private to published storage.
5. Verify published object existence, exact byte size, media type, and trusted
   SHA-256 metadata or streamed checksum.
6. Only after every critical object verifies, commit the PostgreSQL publication.
7. Derive public delivery URLs from the trusted base URL and stored object keys.

This ordering can create an unreferenced published object if the database
transaction later fails. It cannot create a committed publication pointing to
a missing object. Unreferenced objects are retained and reclaimed through a
separate reviewed cleanup process.

The publish operation is idempotent:

- copying bytes already present at the same content-hashed key succeeds only
  when verified metadata matches;
- a mismatched existing object fails closed;
- retrying database publication follows existing publication uniqueness and
  immutability constraints;
- partial copy progress is safe to resume.

## Delivery Resolution

The Lobby and future room bootstrap paths must not issue S3 calls.

The application:

1. resolves approved metadata from PostgreSQL;
2. validates the stored key and variant through a typed registry;
3. constructs `deliveryUrl` from `ASSET_PUBLIC_BASE_URL` and the canonical key;
4. returns checksum, dimensions, media type, and byte size needed by loaders.

The public base URI:

- is production-required configuration;
- uses HTTPS outside local development;
- contains an exact allowed scheme and host;
- has no credentials, user info, query, or fragment;
- is normalized once at startup;
- cannot be supplied by dynamic content or a request.

Client loaders use the exact immutable URL and checksum. They must not rewrite
hosts, follow content-provided redirects, or fall back to an arbitrary URL.

## Cache And CDN Policy

Published objects use:

```text
Cache-Control: public, max-age=31536000, immutable
```

Manifests and Lobby bootstrap responses retain shorter, version-aware caching.
Because object keys are immutable and content-hashed, publication and rollback
do not require overwriting or purging a valid runtime object.

Negative caching is considered during publication: the public object must be
verified through the delivery path before a database publication becomes
effective. A new key must not be exposed to clients before it is available.

Local MinIO delivery may use a local public endpoint without a CDN. Production
R2 delivery requires an approved custom delivery domain before launch; provider
development domains are not the production cache contract.

## Manifest Decision

Phase 4.3 does not add a separate asset-manifest table.

For the first vertical slice, the combination of:

- immutable `content_versions`;
- explicit `content_version_assets`;
- exact immutable `asset_variants`;
- immutable `content_publications`;

is the authoritative manifest-equivalent representation.

The room contract will pin publication/content-version identifiers and their
resolved asset dependency set. A separate manifest aggregate requires a future
schema review only if cross-content room composition cannot be pinned safely
with the existing model. It must not be invented preemptively.

## Runtime And Realtime Boundaries

- React loads Lobby media from immutable public delivery URLs.
- Phaser loads only the selected room dependency set.
- Asset loading completes before a room becomes playable.
- No object-storage, CDN, PostgreSQL, RabbitMQ, Admin, or AI call occurs per
  attack or render frame.
- Active rooms retain pinned versions when new content is published.
- Missing critical boss, player, map, telegraph, or required audio metadata
  fails room entry safely.
- Missing optional decorative media uses an approved local fallback.

## Quality And Performance Gates

Publication must enforce the approved budgets:

| Budget | Mobile | Desktop |
| --- | ---: | ---: |
| Initial Lobby reviewed assets | 1.5MB | 1.5MB |
| Initial Lobby audio after interaction | 800KB | 800KB |
| First room visual assets | 5MB | 10MB |
| First room audio | 1.5MB | 3MB |
| Runtime memory target | 128MB | 256MB |
| Texture atlas maximum | 2048x2048 | Quality-tier scoped |

Critical clarity is not removed to meet a budget. Optional layers, particles,
secondary animation, and audio concurrency degrade first.

Every runtime asset is decoded and visually or audibly reviewed at its actual
minimum and maximum presentation sizes before publication.

## Security

- Runtime credentials use bucket-scoped least privilege.
- Upload signing has private-bucket write permission only.
- Publishing has private read plus published write/head permission.
- Public delivery has read-only object access and no list permission.
- Cleanup credentials are separate from ordinary runtime credentials.
- React and Phaser never receive storage credentials.
- CORS allows only approved frontend/Admin origins and required methods.
- Presigned URLs are redacted as secrets.
- Provider errors are translated without bucket, key, endpoint, or credential
  leakage.
- Startup fails closed in production when required storage configuration is
  absent, malformed, insecure, or uses the same bucket for private and public
  zones.

## Admin And Human Review Boundary

Asset management remains data-driven:

```text
draft
  -> quarantine and technical validation
  -> human review
  -> approved candidate
  -> copy and verify published variants
  -> content preview
  -> publication
  -> archive or append-only rollback
```

AI generation never publishes directly. Dynamic payloads cannot provide URLs,
object keys, HTML, scripts, CSS, routes, commands, permissions, or executable
configuration.

Phase 4.3 storage foundation does not approve a full Admin UI. Controlled asset
ingestion, provenance/licensing metadata, moderation, and publish APIs require
their own scoped design and approval before production media is uploaded.

## Configuration Contract

Expected environment references:

```text
OBJECT_STORAGE_ENDPOINT
OBJECT_STORAGE_REGION
OBJECT_STORAGE_PRIVATE_BUCKET
OBJECT_STORAGE_PUBLIC_BUCKET
OBJECT_STORAGE_ACCESS_KEY
OBJECT_STORAGE_SECRET_KEY
OBJECT_STORAGE_PATH_STYLE
ASSET_PUBLIC_BASE_URL
```

No value belongs in source control, command-line arguments, URLs, logs, tests,
or frontend bundles.

Local MinIO credentials will be generated or supplied through ignored local
configuration during the implementation phase. Production R2 credentials,
buckets, custom domain, DNS, cache, and CORS configuration are requested only
when production deployment reaches that phase.

## Failure Policy

| Failure | Required behavior |
| --- | --- |
| Private upload unavailable | Fail upload; existing published content remains usable |
| Validation timeout | Keep asset unapproved and retry explicitly |
| Partial publish copy | Do not commit publication; retry verified missing objects |
| Published metadata mismatch | Fail closed and raise an operations alert |
| PostgreSQL publish failure | Leave unreferenced immutable copy for later cleanup |
| CDN/storage outage | Bounded retry; safe Lobby return or room-entry failure |
| Optional asset missing | Use an approved packaged fallback |
| Critical asset missing | Prevent room entry; never start a degraded unfair battle |

Cleanup is mark, reference-check, delay, delete, and audit. It is never an
unbounded bucket scan or direct Admin delete.

## Validation Requirements

The implementation pull requests must include:

- configuration validation tests for local and production profiles;
- object-key canonicalization and traversal tests;
- MinIO integration tests for put, head, copy, presign, metadata, and mismatch;
- private/public bucket isolation tests;
- presigned expiry, method, content-type, and size/checksum policy tests where
  supported;
- public URL host, encoding, and injection tests;
- publication failure and idempotent retry tests;
- no-S3-call Lobby resolution tests;
- critical versus optional asset failure tests;
- aggregate transfer-budget tests;
- cache-header and CORS verification;
- secret scan, backend quality build, Docker build, and dependency review;
- browser loading checks at 390x844 and 1440x900;
- measured transfer, decoded memory, texture count, frame time, and audio voice
  budgets before the vertical slice is accepted.

## Consequences

Benefits:

- stable CDN caching and lower backend load;
- provider portability;
- private human-review workflow;
- immutable rollback and room pinning;
- no live storage dependency in combat.

Costs:

- two storage zones and a prepare-then-commit publish workflow;
- orphan cleanup after rare partial failures;
- an additional S3 SDK dependency and local MinIO service;
- production bucket, domain, CORS, cache, and least-privilege provisioning.

These costs are accepted because they protect runtime performance and prevent
unreviewed or missing media from reaching players.

