package com.game.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Refresh refresh,
        @Valid @NotNull Cookie cookie,
        @Valid @NotNull RateLimit rateLimit
) {

    public record Jwt(
            @NotBlank String issuer,
            String privateKey,
            String publicKey,
            @NotNull Duration accessTokenTtl
    ) {
    }

    public record Refresh(
            @NotNull Duration ttl,
            @Min(16) int tokenBytes
    ) {
    }

    public record Cookie(
            @NotBlank String name,
            @NotBlank String path,
            boolean secure,
            @NotBlank String sameSite
    ) {
    }

    public record RateLimit(
            @NotBlank String keySecret,
            @Min(1) int loginIpLimit,
            @Min(1) int loginIdentityLimit,
            @Min(1) int registerIpLimit,
            @Min(1) int refreshLimit,
            @NotNull Duration window,
            boolean failClosed
    ) {
    }
}
