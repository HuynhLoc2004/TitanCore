package com.game.player.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CompleteOnboardingRequest(
        @NotBlank String displayName,
        @Min(0) long expectedVersion
) {
}
