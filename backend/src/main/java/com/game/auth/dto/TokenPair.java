package com.game.auth.dto;

import java.time.Instant;

public record TokenPair(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken
) {
}
