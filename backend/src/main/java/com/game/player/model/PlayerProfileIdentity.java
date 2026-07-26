package com.game.player.model;

import java.time.Instant;
import java.util.UUID;

public record PlayerProfileIdentity(
        UUID id,
        String displayName,
        String displayNameKey,
        long version,
        Instant onboardingCompletedAt
) {

    public boolean onboardingCompleted() {
        return onboardingCompletedAt != null;
    }
}
