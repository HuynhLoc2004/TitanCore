# Development Setup

## Prerequisites

- Java 21
- Docker
- Docker Compose
- Node.js 20 or newer

## Backend

```bash
cd backend
./gradlew bootRun
```

## Frontend

```bash
cd frontend
npm install
npm run dev
```

## Docker

```bash
cp .env.example .env
docker compose up --build
```

## Local Object Storage

Object storage is optional until an asset-delivery feature is exercised. Start
the pinned MinIO service explicitly:

```bash
docker compose --profile assets up -d minio
```

Before starting it, configure non-empty `MINIO_ROOT_USER` and
`MINIO_ROOT_PASSWORD` in ignored local environment configuration. Do not reuse
Google, JWT, database, or application object-storage credentials.

Use the local MinIO Console at `http://localhost:9001` to provision:

- a private bucket matching `OBJECT_STORAGE_PRIVATE_BUCKET`;
- a separate published bucket matching `OBJECT_STORAGE_PUBLIC_BUCKET`;
- a dedicated application access key and secret;
- private-bucket object read/write permission;
- published-bucket object read/write permission;
- no bucket administration, user administration, policy administration, or
  unrestricted delete permission for the application identity;
- anonymous read only for objects in the published bucket when exercising
  local browser delivery.

Before a browser-based Admin upload flow is enabled, configure private-bucket
CORS for the exact approved local frontend origin, `PUT`/`HEAD` only, and the
signed request headers returned by the backend. Do not use a wildcard origin,
and do not enable bucket listing or browser delete access.

Set `OBJECT_STORAGE_ENABLED=true` only after those resources exist. The backend
uses the application identity through `OBJECT_STORAGE_ACCESS_KEY` and
`OBJECT_STORAGE_SECRET_KEY`; it must never use the MinIO root identity.

Do not put credentials in commands, source files, screenshots, logs, or
documentation. Production R2 provisioning is a separate deployment phase.
