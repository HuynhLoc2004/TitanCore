# Security Rules

## Secrets

- All secrets must come from environment variables or secret managers.
- `.env` is local only and must not be committed.
- Logs must mask tokens, passwords, API keys, cookies, OTPs, payment data, and personal secrets.

## Backend Security

- Use Spring Security.
- Use JWT access and refresh token rotation.
- Store blacklisted tokens in Redis.
- Validate all inputs with Bean Validation.
- Return unified error responses.
- Never expose stack traces.
- Use CORS whitelist.
- Rate limit sensitive endpoints.

## Payment

- Only cosmetics are allowed.
- No pay-to-win mechanics.
- Payment webhooks must be verified before state changes.

