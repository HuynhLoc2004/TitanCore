# Coding Standards

## General

- Use SOLID, DRY, KISS, and clean naming.
- Prefer Java records for immutable DTOs.
- Keep classes small and module-owned.
- Avoid god classes.
- Avoid unnecessary abstraction.
- Do not leave TODO, FIXME, commented code, `System.out.println`, or `console.log`.

## Backend

- Controllers only handle HTTP boundary concerns.
- Services hold application use cases.
- Repositories access persistence only.
- Validators protect module inputs.
- Mappers convert between DTOs and domain/entity types.

## Frontend

- React owns UI screens and state.
- Phaser owns game rendering.
- WebSocket clients send intentions only.
- Never trust client-side damage, rewards, boss HP, or ranking values.

