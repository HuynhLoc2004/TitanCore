# RabbitMQ Architecture

RabbitMQ is used only for asynchronous processing. It is forbidden inside realtime attack execution.

## Queues

| Queue | Producer | Consumer | Purpose |
| --- | --- | --- | --- |
| `reward.queue` | Battle completion service | Reward worker | Create reward claims and inventory grants |
| `notification.queue` | Reward, payment, system events | Notification worker | Create in-app notifications |
| `payment.queue` | Payment webhook handler | Payment worker | Verify and finalize payment side effects |
| `mail.queue` | Reward/admin/system events | Mail worker | Send persistent mail |
| `analytics.queue` | Gameplay summary events | Analytics worker | Persist aggregate analytics |
| `audit.queue` | Security/payment/admin actions | Audit worker | Persist audit logs |

## Retry Strategy

Each queue has:

- Main queue.
- Retry queue with delayed delivery.
- Dead letter queue.

Pattern:

```text
*.queue -> *.retry.queue -> *.queue
*.queue -> *.dlq after max attempts
```

## Idempotency

Every async message must include:

- `eventId`
- `eventType`
- `occurredAt`
- `idempotencyKey`
- Minimal payload

Consumers must check idempotency before writing side effects. Payment and reward consumers must be strictly idempotent.

## Reward Processing Guarantees

Reward processing is logically exactly-once, even though RabbitMQ delivery is at-least-once.

Required durable idempotency key:

```text
reward_claims(player_id, reward_id, source_type, source_id)
```

The reward consumer must execute one PostgreSQL transaction containing:

- Insert or confirm `reward_claims`.
- Apply inventory mutation.
- Insert immutable reward ledger entry.
- Mark processing result by idempotency key.

If the same RabbitMQ message is delivered again, the consumer reads the existing idempotency record and returns success without duplicating inventory or rewards.

## Reliable Publication

Battle completion must first be represented as durable PostgreSQL state. RabbitMQ publication should be driven from that durable state, preferably with a transactional outbox table in a future implementation phase.

Flow:

```text
persist battle completion
persist outbox event in same transaction
publisher reads unpublished outbox events
publish to RabbitMQ
mark outbox event published
```

This avoids losing reward events if the server crashes after database commit but before RabbitMQ publish.

## Message Size

Messages should contain identifiers and compact context, not full object graphs. Consumers load durable data from PostgreSQL when needed.

## Failure Handling

- Transient failures retry.
- Validation failures go to DLQ.
- DLQ requires manual inspection.
- Consumers must not log secrets or raw payment payloads.
