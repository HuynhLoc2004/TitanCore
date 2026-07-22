# Performance Rules

## Targets

- API target: under 100ms for common requests.
- WebSocket latency: minimal and stable.
- Smooth rendering for 100-200 concurrent players on one VPS.

## Gameplay Loop

Allowed:

- WebSocket
- Server-side validation
- Redis atomic operations
- Room broadcast

Forbidden:

- PostgreSQL query per attack
- RabbitMQ message per attack
- AI call inside gameplay
- Blocking long-running work inside socket handlers

## Review Checklist

- Check Redis key design.
- Check database query count.
- Check N+1 risks.
- Check memory retention.
- Check broadcast payload size.
- Check thread safety and race conditions.

