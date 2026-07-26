package com.game.auth.dto;

import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String email,
        String username,
        String role,
        String status
) {
}
