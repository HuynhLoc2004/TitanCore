# AI Rules

AI is allowed only outside realtime gameplay.

The provider-neutral storage direction in this document supersedes
Cloudinary-specific asset-storage references in earlier architecture documents.
Those references are historical and do not approve a Cloudinary integration.

## Allowed AI Tasks

- Boss generation
- Monster generation
- Map generation
- Quest generation
- Skill generation
- Lore generation
- Item generation
- Reward generation
- Story generation
- Image prompt generation

## Required Controls

- AI-generated game definitions return schema-versioned JSON only.
- Media-generation jobs return validated metadata plus a private quarantine
  object reference; binary output never enters gameplay responses or PostgreSQL.
- Backend validates all AI output.
- Cache prompts by hash.
- Cache responses.
- Store accepted content in PostgreSQL.
- Never regenerate existing content unnecessarily.
- AI-generated images, audio, and content assets are untrusted drafts.
- Every AI-generated asset requires human review before publishing.
- Asset generation and optimization are asynchronous and remain outside
  realtime gameplay.
- Binary image and audio data is never stored in PostgreSQL.
- PostgreSQL stores validated generation metadata, review state, object keys,
  checksums, and published references only.
- Object storage is accessed through a provider-neutral S3-compatible
  abstraction: MinIO locally and Cloudflare R2 in production.
- Draft assets remain private until approved; published assets use immutable,
  content-hashed object keys and versioned manifests.
- AI output cannot directly mutate published content or active battle state.

See [Asset Pipeline](ASSET_PIPELINE.md) for the approved review, storage, and
publishing boundary.
