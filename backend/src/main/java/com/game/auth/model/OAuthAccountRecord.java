package com.game.auth.model;

import java.time.Instant;
import java.util.UUID;

public record OAuthAccountRecord(
        UUID id,
        UUID userId,
        String provider,
        String providerSubject,
        String emailAtLinkTime,
        boolean providerEmailVerified,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt
) {
}
