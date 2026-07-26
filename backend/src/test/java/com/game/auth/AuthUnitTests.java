package com.game.auth;

import com.game.auth.config.AuthProperties;
import com.game.auth.model.UserAccount;
import com.game.auth.model.UserStatus;
import com.game.auth.service.AuthException;
import com.game.auth.service.JwtService;
import com.game.auth.service.RateLimiterService;
import com.game.auth.service.TokenHashService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthUnitTests {

    @Test
    void bcryptCostTwelveHashesAndVerifiesPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String hash = encoder.encode("very-secure-password");

        assertThat(hash).startsWith("$2");
        assertThat(encoder.matches("very-secure-password", hash)).isTrue();
        assertThat(encoder.matches("wrong-password", hash)).isFalse();
    }

    @Test
    void tokenHashIsStableAndDoesNotReturnRawToken() {
        TokenHashService service = new TokenHashService();

        String first = service.sha256("opaque-refresh-token");
        String second = service.sha256("opaque-refresh-token");

        assertThat(first).isEqualTo(second);
        assertThat(first).doesNotContain("opaque-refresh-token");
        assertThat(first).hasSize(64);
    }

    @Test
    void jwtSignsValidatesAndRejectsInvalidSignature() {
        KeyPair keys = TestKeys.generateRsa();
        AuthProperties properties = properties(TestKeys.privatePem(keys), TestKeys.publicPem(keys), Duration.ofMinutes(10));
        Clock clock = Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC);
        JwtService service = new JwtService(properties, clock, new StandardEnvironment());
        UUID sessionId = UUID.randomUUID();
        UserAccount user = new UserAccount(UUID.randomUUID(), "a@example.com", "alpha", "hash",
                UserStatus.ACTIVE, "PLAYER", null, null);

        JwtService.IssuedAccessToken issuedAccess = service.issue(user, sessionId);
        JwtService.AuthPrincipal principal = service.validate(issuedAccess.value());

        assertThat(principal.userId()).isEqualTo(user.id());
        assertThat(principal.sessionId()).isEqualTo(sessionId);
        assertThat(principal.role()).isEqualTo("PLAYER");
        assertThatThrownBy(() -> service.validate(issuedAccess.value() + "x")).hasMessage("Unauthorized");
    }

    @Test
    void jwtRejectsExpiredToken() {
        KeyPair keys = TestKeys.generateRsa();
        AuthProperties properties = properties(TestKeys.privatePem(keys), TestKeys.publicPem(keys), Duration.ofMillis(1));
        JwtService service = new JwtService(properties, Clock.systemUTC(), new StandardEnvironment());
        UserAccount user = new UserAccount(UUID.randomUUID(), "a@example.com", "alpha", "hash",
                UserStatus.ACTIVE, "PLAYER", null, null);

        JwtService.IssuedAccessToken issuedAccess = service.issue(user, UUID.randomUUID());

        assertThatThrownBy(() -> {
            Thread.sleep(20);
            service.validate(issuedAccess.value());
        }).hasMessage("Unauthorized");
    }

    @Test
    void rateLimiterFailsOpenOrClosedWhenRedisIsUnavailableByConfiguration() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        RateLimiterService failOpen = new RateLimiterService(redisTemplate, properties(false));
        failOpen.checkLogin("127.0.0.1", "alpha@example.com");

        RateLimiterService failClosed = new RateLimiterService(redisTemplate, properties(true));
        assertThatThrownBy(() -> failClosed.checkLogin("127.0.0.1", "alpha@example.com"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Authentication temporarily unavailable");
    }

    private AuthProperties properties(String privateKey, String publicKey, Duration accessTtl) {
        return new AuthProperties(
                new AuthProperties.Jwt("test", privateKey, publicKey, accessTtl),
                new AuthProperties.Refresh(Duration.ofDays(14), 48),
                new AuthProperties.Cookie("refresh_token", "/api/auth", false, "Lax"),
                new AuthProperties.RateLimit("test-secret", 20, 10, 10, 60, Duration.ofMinutes(15), false)
        );
    }

    private AuthProperties properties(boolean failClosed) {
        return new AuthProperties(
                new AuthProperties.Jwt("test", "", "", Duration.ofMinutes(10)),
                new AuthProperties.Refresh(Duration.ofDays(14), 48),
                new AuthProperties.Cookie("refresh_token", "/api/auth", false, "Lax"),
                new AuthProperties.RateLimit("test-secret", 20, 10, 10, 60, Duration.ofMinutes(15), failClosed)
        );
    }
}
