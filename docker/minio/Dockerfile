ARG MINIO_VERSION=RELEASE.2025-10-15T17-29-55Z

FROM golang:1.24.8-alpine@sha256:3d78beb141d98f42337f1252ecf2a5f20374109929a4c3f6817f9e4179cc0ae5 AS build

ARG MINIO_VERSION
RUN CGO_ENABLED=0 GOBIN=/out \
    go install "github.com/minio/minio@${MINIO_VERSION}"

FROM alpine:3.22@sha256:14358309a308569c32bdc37e2e0e9694be33a9d99e68afb0f5ff33cc1f695dce

ARG MINIO_VERSION
LABEL org.opencontainers.image.source="https://github.com/minio/minio" \
      org.opencontainers.image.version="${MINIO_VERSION}" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later"

RUN apk add --no-cache ca-certificates curl \
    && addgroup -S minio \
    && adduser -S -G minio minio \
    && mkdir -p /data \
    && chown minio:minio /data

COPY --from=build /out/minio /usr/local/bin/minio

USER minio
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
