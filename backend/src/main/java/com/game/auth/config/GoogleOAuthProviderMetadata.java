package com.game.auth.config;

import java.time.Duration;

public record GoogleOAuthProviderMetadata(
        String authorizationUri,
        String tokenUri,
        String jwksUri,
        String issuer,
        Duration stateTtl,
        Duration providerTimeout,
        Duration clockSkew
) {

    private static final String GOOGLE_AUTHORIZATION_URI = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String GOOGLE_ISSUER = "https://accounts.google.com";

    public static GoogleOAuthProviderMetadata official() {
        return new GoogleOAuthProviderMetadata(
                GOOGLE_AUTHORIZATION_URI,
                GOOGLE_TOKEN_URI,
                GOOGLE_JWKS_URI,
                GOOGLE_ISSUER,
                Duration.ofMinutes(5),
                Duration.ofSeconds(5),
                Duration.ofSeconds(30)
        );
    }
}
