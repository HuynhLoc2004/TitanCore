package com.game.auth.security;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, UUID sessionId, String role) {
}
