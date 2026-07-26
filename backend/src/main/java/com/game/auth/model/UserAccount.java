package com.game.auth.model;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(
        UUID id,
        String email,
        String username,
        String passwordHash,
        UserStatus status,
        String role,
        Instant emailVerifiedAt,
        Instant lastLoginAt
) {
}
