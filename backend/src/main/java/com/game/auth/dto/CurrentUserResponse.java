package com.game.auth.dto;

import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String email,
        String role,
        String status,
        ProfileIdentityResponse profile
) {
}
