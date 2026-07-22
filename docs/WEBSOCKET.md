# WebSocket

## Responsibilities

- Authenticate connection.
- Track online state.
- Support heartbeat.
- Support reconnect.
- Support session recovery.
- Broadcast room updates.
- Clean up disconnects.

## Security

- Validate all incoming messages.
- Apply cooldowns server-side.
- Calculate damage server-side.
- Reject replayed or malformed actions.
- Never trust client-provided game state.

