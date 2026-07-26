package com.game.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 320) String login,
        @NotBlank @Size(max = 128) String password,
        @Size(max = 80) String deviceLabel
) {
}
