# Role-Based Access Control

Phase 2.3 defines TitanCore admin roles and permission boundaries. It is documentation only.

No application code, migrations, APIs, entities, repositories, services, controllers, WebSocket implementation, or admin UI is approved by this document.

## Goals

- Replace broad `ADMIN` thinking with least-privilege permissions.
- Keep admin access separate from normal player gameplay access.
- Require stronger authentication for sensitive operations.
- Make every material admin action auditable.
- Keep the model simple enough for the 100-200 concurrent user MVP.

## Admin Roles

### `SUPER_ADMIN`

Purpose: Owns platform-level administration and emergency access.

Allowed:

- Manage admin users and roles.
- Approve high-risk runtime config changes.
- Perform emergency locks and platform-level recovery actions.
- Access audit records.

Restrictions:

- Should not be used for daily content, support, or finance work.
- Sensitive actions require step-up authentication.

### `CONTENT_ADMIN`

Purpose: Manages game content lifecycle.

Allowed:

- Create and edit drafts for bosses, maps, items, rewards, and events.
- Review content validation results.
- Publish approved content.
- Roll back published content.
- Review AI-generated content and promote it into drafts.

Restrictions:

- Cannot moderate users unless separately granted.
- Cannot approve refunds.
- Cannot change admin roles.

### `MODERATOR`

Purpose: Handles player safety and conduct.

Allowed:

- View player moderation history.
- Create warnings.
- Mute players.
- Ban players.
- Lock accounts when policy permits.
- Attach evidence to moderation actions.
- Reverse moderation actions when permitted.

Restrictions:

- Cannot change payment state.
- Cannot publish content.
- Cannot grant rewards directly unless a later support workflow explicitly approves it.

### `FINANCE_ADMIN`

Purpose: Handles PayOS payment operations.

Allowed:

- Review PayOS orders and webhook events.
- Run reconciliation review.
- Request refunds.
- Approve refunds requested by a different finance admin.
- View payment operation cases.

Restrictions:

- Cannot approve their own refund request.
- Cannot publish game content.
- Cannot moderate users except payment-related account holds if explicitly granted.

### `SUPPORT_ADMIN`

Purpose: Handles player support without broad operational power.

Allowed:

- View user profile, account state, payment status summary, rewards, and inventory summary.
- View delivery history for notifications and email.
- Create support notes.
- Escalate moderation, payment, or anti-cheat cases.

Restrictions:

- Cannot ban, refund, publish content, or change runtime config by default.
- Cannot view raw secrets or full payment metadata.

### `OPS_VIEWER`

Purpose: Read-only operational monitoring.

Allowed:

- View health dashboards.
- View queue depth, Redis status, active room counts, and outbox backlog.
- View recent deployment and incident summaries.

Restrictions:

- No mutation permissions.
- No access to raw sensitive player data unless explicitly granted.

## Permission Model

Roles are collections of permissions. Implementation should check permissions, not role names, at the action boundary.

Recommended permission naming:

```text
admin.user.read
admin.user.manage_roles
moderation.case.read
moderation.action.create
moderation.action.reverse
content.draft.write
content.review
content.publish
content.rollback
ai_content.review
ai_content.promote_to_draft
payment.order.read
payment.webhook.review
payment.reconciliation.run
payment.refund.request
payment.refund.approve
support.case.read
support.note.write
email.template.write
email.template.publish
email.delivery.read
config.read
config.write
config.emergency_toggle
ops.dashboard.read
audit.read
```

Future schema proposal, not an approved migration:

- `admin_roles`
- `admin_permissions`
- `admin_role_permissions`
- `admin_user_roles`

For the first implementation, a small static permission registry may be acceptable if documented and tested. Database-backed RBAC should be introduced when admin role assignment must change at runtime.

## Role To Permission Matrix

| Permission area | SUPER_ADMIN | CONTENT_ADMIN | MODERATOR | FINANCE_ADMIN | SUPPORT_ADMIN | OPS_VIEWER |
| --- | --- | --- | --- | --- | --- | --- |
| Admin user and role management | Yes | No | No | No | No | No |
| Content draft/write | Yes | Yes | No | No | No | No |
| Content publish/rollback | Step-up | Step-up | No | No | No | No |
| AI content review | Yes | Yes | No | No | No | No |
| Moderation actions | Yes | No | Yes | No | Escalate only | No |
| Payment read | Yes | No | No | Yes | Summary only | No |
| Refund request | Step-up | No | No | Step-up | No | No |
| Refund approve | Step-up | No | No | Step-up, different requester | No | No |
| Email templates | Yes | No | No | No | Limited | No |
| Runtime config | Step-up | No | No | No | No | Read only |
| Ops dashboard | Yes | Read only | Read only | Read only | Read only | Read only |
| Audit read | Yes | No | Own area | Own area | Own notes | No |

## Admin Authentication

Admin authentication is separate from normal gameplay session policy.

Rules:

- Admin access requires a user account in good standing.
- Admin sessions should have shorter idle and absolute timeouts than player sessions.
- Admin session state should be distinguishable from player session state.
- Refresh token rotation still applies.
- Admin sessions should be invalidated after role changes, account lock, password reset, or suspected compromise.
- Admin routes must require explicit admin permission checks.

Future schema proposal, not an approved migration:

- Add admin session metadata to `user_sessions`, or introduce `admin_sessions` only if separation becomes necessary.
- Store admin MFA factors separately from gameplay profile data.

## MFA And Step-Up Authentication

MFA is required for admin accounts before sensitive operations are implemented.

Step-up authentication means the admin must recently re-confirm identity before the action executes.

Step-up required for:

- role or permission changes;
- content publish and rollback;
- runtime emergency toggles;
- account lock and long ban actions;
- refund request and refund approval;
- AI content promotion to publishable draft;
- audit export;
- payment reconciliation correction.

Step-up must not print or log secrets, OTPs, recovery codes, or provider responses.

Future schema proposal, not an approved migration:

- `admin_mfa_factors`
- `admin_step_up_challenges`

## Sensitive Action Confirmation

Sensitive actions require explicit confirmation in addition to permission checks.

Confirmation must capture:

- action type;
- target;
- reason code;
- admin actor;
- timestamp;
- step-up challenge reference when applicable.

The confirmation record should be linked to the immutable audit event.

## Audit Requirements

Every successful sensitive admin action must create an immutable audit record.

Failed sensitive attempts should also be recorded when useful for security review.

Audit logs must include:

- actor;
- permission used;
- action;
- target type and ID;
- reason;
- request ID;
- IP address;
- user-agent hash;
- before/after summary when applicable;
- timestamp.

Audit logs must not include:

- raw passwords;
- tokens;
- API keys;
- OTPs;
- raw payment secrets;
- full provider webhook payloads containing sensitive data.

## Separation Of Duties

Separation of duties is required for controlled refunds.

Rules:

- The admin requesting a refund cannot approve the same refund.
- Refund approval requires `payment.refund.approve`.
- Refund execution must reference both requester and approver.
- Manual reward or inventory compensation related to payment disputes must be linked to the same payment operation case.

Separation of duties is recommended for:

- high-impact content publish;
- runtime config changes that affect rewards, payments, or room creation;
- long-duration account bans.

## Runtime Permission Enforcement

Permission checks should happen at service/application boundaries before state mutation.

Required enforcement points:

- Admin REST APIs when implemented.
- Async admin workers when executing approved operations.
- Content publish operations.
- Payment refund operations.
- Moderation actions.
- Runtime config changes.

WebSocket combat handlers must not perform admin permission checks because admin actions must not be part of the combat loop.

## Performance And Availability

RBAC checks must be efficient:

- Cache resolved admin permissions for a short TTL.
- Invalidate cache on role or permission changes.
- Do not query RBAC tables per attack.
- Do not attach admin permission state to player room presence.
- Keep admin dashboards read-only by default.

For the MVP, role changes are rare. Simple cache invalidation is acceptable.

## Deferred Implementation

This document does not approve:

- RBAC database migrations;
- admin API endpoints;
- admin UI;
- MFA provider integration;
- audit export implementation;
- payment refund execution;
- runtime config mutation code.

## Engineering Review

Security:

- The design avoids broad `ADMIN` authorization for sensitive operations.
- MFA and step-up are required for high-risk actions.
- Refund requester and approver are separated.

Maintainability:

- Permission strings are stable and testable.
- Roles are business groupings, not hardcoded authorization logic.
- A static registry can support the MVP before database-backed RBAC is necessary.

Operational safety:

- Sensitive actions require reason and audit records.
- Admin session invalidation protects role changes.
- Audit data is explicit but redacted.

MVP proportionality:

- Six roles cover expected admin needs without creating dozens of specialized roles.
- Schema ideas remain future proposals, not approved migrations.

Realtime performance:

- RBAC is kept away from attack handling and Redis Lua operations.
- Permission caches are short-lived and invalidated on admin changes.
