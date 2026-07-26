package com.game.auth.service;

import com.game.auth.config.AuthProperties;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed SecureRandom is intentionally injected and not exposed")
public class RefreshTokenGenerator {

    private final AuthProperties authProperties;
    private final SecureRandom secureRandom;

    public RefreshTokenGenerator(AuthProperties authProperties, SecureRandom secureRandom) {
        this.authProperties = authProperties;
        this.secureRandom = secureRandom;
    }

    public String generate() {
        byte[] bytes = new byte[authProperties.refresh().tokenBytes()];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
