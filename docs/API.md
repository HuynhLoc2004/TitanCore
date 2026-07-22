# API

## Rules

- Use REST for non-realtime workflows.
- Use WebSocket for realtime gameplay.
- All request DTOs must be validated.
- All errors use unified error response.
- Swagger must be updated for public endpoints.
- Authentication is required by default unless explicitly documented.

## Current Technical Endpoint

- `GET /api/health`

