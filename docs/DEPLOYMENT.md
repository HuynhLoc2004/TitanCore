# Deployment

## Target

Single VPS deployment using Docker Compose and Nginx.

## Required Production Checks

- Use production secrets.
- Use HTTPS at Nginx or upstream proxy.
- Disable Swagger unless explicitly allowed.
- Run database migrations.
- Verify Redis persistence configuration.
- Verify RabbitMQ credentials.
- Run smoke tests after deployment.

