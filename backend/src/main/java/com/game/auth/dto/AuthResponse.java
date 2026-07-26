package com.game.auth.dto;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        CurrentUserResponse user
) {
}
