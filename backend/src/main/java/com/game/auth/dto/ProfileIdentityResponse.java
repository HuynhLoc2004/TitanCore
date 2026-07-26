package com.game.auth.dto;

import java.util.UUID;

public record ProfileIdentityResponse(
        UUID id,
        String displayName,
        String onboardingStatus,
        long version
) {
}
