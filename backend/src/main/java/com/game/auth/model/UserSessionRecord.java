package com.game.auth.model;

import java.time.Instant;
import java.util.UUID;

public record UserSessionRecord(
        UUID id,
        UUID userId,
        String sessionKey,
        String deviceLabel,
        String ipAddress,
        Instant startedAt,
        Instant lastSeenAt,
        Instant endedAt
) {
}
