# TitanCore Project Rules

## Non-Negotiable Rules

- Do not commit directly to `main`.
- Do not develop directly on `develop`.
- Start all work from an approved branch.
- Do not merge automatically.
- Do not implement business logic without an approved issue and branch.
- Do not hardcode secrets.
- Do not expose stack traces or raw exceptions to clients.
- Do not put AI, PostgreSQL queries, or RabbitMQ inside the realtime gameplay loop.

## Quality Gate

Work is complete only when:

- Build succeeds.
- Unit tests pass.
- Integration tests pass when applicable.
- Static analysis passes.
- Security review passes.
- Performance review passes.
- Documentation is updated.
- Pull request review is completed.
- User approves merge.

## Source Of Truth

The `docs/` folder is the source of truth for architecture, workflow, security, performance, API, database, WebSocket, and deployment rules.

