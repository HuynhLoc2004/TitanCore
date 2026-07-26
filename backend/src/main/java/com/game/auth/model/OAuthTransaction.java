package com.game.auth.model;

public record OAuthTransaction(
        String state,
        String nonce,
        String codeVerifier,
        String purpose,
        String redirectUri
) {
}
