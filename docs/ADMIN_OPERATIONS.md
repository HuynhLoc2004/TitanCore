# Admin Operations Architecture

Phase 2.3 defines the administrative and operations architecture for TitanCore. It is documentation only.

No application code, migrations, APIs, entities, repositories, services, controllers, WebSocket implementation, or admin UI is approved by this document.

## Scope

TitanCore MVP targets 100-200 concurrent users. Admin operations must therefore be practical, auditable, and isolated from realtime combat without introducing a large enterprise control plane too early.

This phase covers:

- Player moderation and account safety.
- Content draft, review, publish, and rollback workflows.
- Human approval for AI-generated content.
- Payment operations for PayOS webhook review, reconciliation, and controlled refunds.
- Email template and delivery-history operations.
- Feature flags, emergency toggles, and runtime game configuration.
- Anti-cheat investigation workflow.
- Immutable admin audit requirements.
- Performance isolation from Redis and WebSocket combat paths.

## Operating Principles

- Admin actions must be least-privilege and role-scoped.
- Sensitive actions require step-up authentication.
- Every material admin action must create an immutable audit record.
- Admin workflows must use PostgreSQL as the durable source of truth.
- Admin work must never run inside the realtime gameplay loop.
- Admin dashboards must read from durable state, snapshots, or derived read models, not from hot attack-path Redis operations.
- Manual operations that affect payment, rewards, inventory, moderation, or published game content must be reviewable after the fact.

## Moderation Workflow

Moderation covers account lock, ban, mute, expiry, reason, evidence, and review state.

Recommended workflow:

1. Moderator opens a case against a user or player profile.
2. Moderator selects an action type:
   - `BAN`
   - `MUTE`
   - `LOCK_ACCOUNT`
   - `WARN`
   - `UNBAN`
   - `UNMUTE`
   - `UNLOCK_ACCOUNT`
3. Moderator provides reason code, free-text note, expiry when applicable, and evidence references.
4. System records the action as immutable audit data.
5. Active sessions are invalidated when account access changes.
6. Gameplay services observe account status before allowing room join, chat, payment, or reward claims.

Future schema proposal, not an approved migration:

- Use one generalized `moderation_actions` record rather than separate tables for every action type.
- Link actions to `users`, `player_profiles`, and `audit_logs`.
- Store evidence references as metadata or normalized attachment records only when needed.

Minimum proposed fields:

| Field | Purpose |
| --- | --- |
| `id` | Action identity |
| `target_user_id` | Account being moderated |
| `target_player_id` | Player profile when applicable |
| `action_type` | Ban, mute, lock, warn, or reversal |
| `status` | Pending, active, expired, reversed |
| `reason_code` | Stable moderation reason |
| `reason_note` | Human explanation, no secrets |
| `evidence` | JSON references to battle, chat, payment, or support records |
| `starts_at` | Effective time |
| `expires_at` | Optional expiry |
| `created_by_admin_id` | Actor |
| `reviewed_by_admin_id` | Optional reviewer |
| `reversed_by_admin_id` | Optional reversing actor |
| `created_at` | Audit time |
| `updated_at` | State transition time |

## Content Operations

Admin-managed content includes bosses, maps, items, rewards, and limited-time events.

Approved concept:

- Content is authored as drafts.
- Drafts are validated before review.
- Review approves or rejects the draft.
- Publishing promotes the approved version to the active domain definition.
- Rollback restores a previous published version.

The MVP should prefer a generalized versioning approach instead of one table per content type.

Future schema proposal, not an approved migration:

- `content_versions`
- `content_publish_events`

Minimum generalized fields:

| Field | Purpose |
| --- | --- |
| `id` | Version identity |
| `content_type` | Boss, map, item, reward, or event |
| `content_id` | Domain definition identity when already created |
| `version_number` | Monotonic version per content object |
| `status` | Draft, in_review, approved, rejected, published, archived |
| `payload` | Validated JSON or normalized projection source |
| `validation_errors` | Latest validation result |
| `created_by_admin_id` | Author |
| `reviewed_by_admin_id` | Reviewer |
| `published_by_admin_id` | Publisher |
| `published_at` | Publish timestamp |
| `created_at` | Creation timestamp |

Publishing rules:

- Publish is a sensitive action.
- Publish must use optimistic locking or a conditional update.
- Publish must create an audit record.
- Rollback is a new publish event pointing to a prior known-good version.
- Published content changes must not mutate active Redis battle state directly.
- New or changed boss definitions apply only to newly created battle rooms unless a later emergency operation is explicitly approved.

## AI-Generated Content Approval

AI never runs inside gameplay.

`ai_generated_content` stores generation metadata and validated JSON payloads only. Human approval is required before AI output becomes a published boss, map, item, reward, event, quest, lore entry, or image prompt.

Approval workflow:

1. AI job creates metadata and JSON payload.
2. Backend validates schema and business constraints.
3. Content admin reviews generated content.
4. Reviewer rejects, requests regeneration, or converts it into a content draft.
5. Publishing follows the same content operations workflow as human-authored content.

AI approval must record:

- prompt hash;
- provider and model metadata;
- schema version;
- validation result;
- reviewer decision;
- published target if promoted.

## PayOS Operations

Payment operations cover PayOS order review, webhook review, reconciliation, and controlled refunds.

Rules:

- Webhooks must be verified before state changes.
- Payment state changes remain idempotent.
- Payment metadata must be minimized and masked in logs.
- Refund requester and refund approver must be different admins.
- Refund approval requires step-up authentication.
- Refund side effects must be auditable and linked to the original payment transaction.

Future schema proposal, not an approved migration:

- Extend `payment_transactions` only if enough for MVP.
- Add generalized operation records when manual review is required:
  - `payment_operation_cases`
  - `payment_operation_events`

Minimum payment operation fields:

| Field | Purpose |
| --- | --- |
| `id` | Operation identity |
| `payment_transaction_id` | Related payment |
| `operation_type` | Webhook review, reconciliation, refund request, refund approval |
| `status` | Open, approved, rejected, completed, failed |
| `amount` | Refund amount when applicable |
| `reason_code` | Stable reason |
| `metadata` | Provider references, never raw secrets |
| `requested_by_admin_id` | Requester |
| `approved_by_admin_id` | Approver, must differ from requester for refunds |
| `created_at` | Creation time |
| `completed_at` | Completion time |

Reconciliation:

- Scheduled jobs compare PayOS-visible state with `payment_transactions`.
- Differences become review cases.
- Automatic correction is allowed only for idempotent, low-risk status confirmation.
- Refunds and manual grants require human approval.

## Email Operations

Email operations include templates and delivery history. This does not approve a mail provider implementation.

Rules:

- Templates are versioned.
- Published templates are immutable.
- Template variables must be allowlisted.
- Delivery history stores provider message ID, recipient user, status, error category, and timestamps.
- Email bodies must not store raw secrets or long-lived tokens.

Future schema proposal, not an approved migration:

- `email_templates`
- `email_template_versions`
- `email_delivery_events`

## Feature Flags And Runtime Configuration

Feature flags and runtime config support safe operation of the MVP without redeploying for every toggle.

Examples:

- Disable new battle room creation.
- Disable payments.
- Disable AI generation jobs.
- Disable reward claiming while keeping completed battle data.
- Change max players per room within approved limits.
- Enable maintenance mode.

Rules:

- Emergency toggles must be fast to read but managed through durable PostgreSQL state.
- Runtime values may be cached in Redis with short TTL.
- Config reads must be outside the per-attack Lua path unless explicitly designed.
- Changes require audit logging.
- High-risk toggles require step-up authentication.

Future schema proposal, not an approved migration:

- Use a generalized `runtime_config_entries` table with versioned values.
- Use a generalized `feature_flags` table for boolean and rollout flags.

## Realtime Operations Dashboard

The realtime operations dashboard is observational for the MVP.

It may show:

- active rooms;
- online player count;
- Redis connectivity;
- RabbitMQ queue depth;
- outbox backlog;
- failed payment/reward events;
- battle finalization retries;
- recent moderation actions.

It must not:

- query PostgreSQL per attack;
- subscribe as a combat participant;
- mutate Redis battle keys directly;
- broadcast to game rooms except through approved admin operations.

Preferred data sources:

- durable PostgreSQL summaries;
- Redis presence counts with TTL;
- RabbitMQ management metrics;
- application health endpoints;
- materialized or cached read models when needed.

## Anti-Cheat Investigation

Anti-cheat review is a human investigation workflow around durable evidence.

Evidence sources:

- battle history;
- sampled or finalized damage logs;
- ranking snapshots;
- reward ledger;
- login history;
- moderation history;
- payment history when abuse involves purchases.

Future schema proposal, not an approved migration:

- Use generalized `investigation_cases` and `investigation_events` if manual review volume justifies it.

Rules:

- Investigations must not block active combat.
- Automated flags create review cases, not immediate punishment, unless the behavior is clearly abusive and policy-approved.
- Sanctions are recorded through the moderation workflow.
- Evidence should reference immutable or append-only records.

## Immutable Admin Audit

Every material admin action must create an immutable audit record.

Audit events must include:

- actor admin user;
- effective permission;
- action;
- target type and ID;
- before and after summary when applicable;
- reason code;
- request ID;
- IP address and user-agent hash when available;
- timestamp.

Sensitive data must be redacted before audit persistence.

Future hardening, not required for MVP:

- hash chaining;
- external log archive;
- write-once storage;
- periodic audit export.

## Performance Isolation

Admin operations must be isolated from realtime gameplay:

- No admin operation may run inside WebSocket attack handlers.
- No admin operation may mutate active room Redis keys directly.
- Heavy admin queries must use pagination and bounded time windows.
- Exports must run asynchronously.
- Content publish affects future room creation unless an emergency operation is explicitly approved.
- Payment, mail, audit, and analytics work should remain asynchronous through RabbitMQ and the outbox where applicable.

## Deferred Implementation

This document does not approve:

- admin REST APIs;
- admin UI;
- database migrations;
- entities or repositories;
- payment refund integration code;
- email provider integration;
- AI publishing implementation;
- realtime dashboard implementation.

## Recommended Future Phases

1. Admin Foundation:
   - RBAC tables and permission checks;
   - admin sessions;
   - MFA and step-up authentication;
   - immutable audit hardening.
2. Moderation Foundation:
   - generalized moderation actions;
   - account lock, ban, mute, expiry, evidence.
3. Content Operations:
   - generalized content versions;
   - draft, review, publish, rollback.
4. Payment Operations:
   - PayOS reconciliation;
   - controlled refund workflow.
5. LiveOps:
   - feature flags;
   - runtime config;
   - operations dashboard.
6. AI Content Publishing:
   - human approval and promotion from generated content to domain definitions.

## Engineering Review

Security:

- Sensitive actions require MFA or step-up authentication.
- Refund request and approval are separated.
- Admin permissions are explicit rather than inferred from a broad admin role.
- Audits must not contain secrets.

Maintainability:

- Generalized action/version records avoid premature table explosion.
- Domain definitions remain separate from AI generation metadata.
- Admin workflows are documented as future proposals, not approved migrations.

Operational safety:

- Publish, rollback, moderation, refund, and config changes are auditable.
- Emergency toggles are allowed but must be permissioned and recorded.
- Scheduled reconciliation creates visible work instead of silent correction.

MVP proportionality:

- The design supports 100-200 concurrent users without a large enterprise admin system.
- Read-only dashboards and bounded workflows come before complex automation.

Realtime performance:

- Admin operations are outside Redis Lua attack paths and WebSocket combat handlers.
- Heavy reads use durable summaries, pagination, and asynchronous jobs.
