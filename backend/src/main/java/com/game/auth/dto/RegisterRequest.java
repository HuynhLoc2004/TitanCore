package com.game.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$") String username,
        @NotBlank @Size(min = 12, max = 128) String password,
        @Size(max = 80) String deviceLabel
) {
}
