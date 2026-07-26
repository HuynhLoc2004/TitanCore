package com.game.auth.model;

public record GoogleIdentity(
        String subject,
        String email,
        boolean emailVerified
) {
}
