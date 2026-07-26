package com.game.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        String deviceLabel,
        String ipAddress,
        Instant startedAt,
        Instant lastSeenAt,
        boolean current
) {
}
