package com.game.auth.model;

import java.time.Instant;
import java.util.UUID;

public record RefreshTokenRecord(
        UUID id,
        UUID userId,
        String tokenHash,
        UUID tokenFamilyId,
        Instant expiresAt,
        Instant revokedAt,
        UUID replacedByTokenId,
        Instant createdAt
) {

    public boolean isActive(Instant now) {
        return revokedAt == null && replacedByTokenId == null && expiresAt.isAfter(now);
    }
}
