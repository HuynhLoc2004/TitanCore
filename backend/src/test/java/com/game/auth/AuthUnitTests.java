package com.game.auth;

import com.game.auth.config.AuthProperties;
import com.game.auth.model.UserAccount;
import com.game.auth.model.UserStatus;
import com.game.auth.security.ClientIpResolver;
import com.game.auth.repository.AuditLogRepository;
import com.game.auth.repository.LoginHistoryRepository;
import com.game.auth.repository.PlayerFoundationRepository;
import com.game.auth.repository.RefreshTokenRepository;
import com.game.auth.repository.UserRepository;
import com.game.auth.repository.UserSessionRepository;
import com.game.auth.service.AuthException;
import com.game.auth.service.AuthService;
import com.game.auth.service.JwtService;
import com.game.auth.service.RateLimiterService;
import com.game.auth.service.RefreshTokenGenerator;
import com.game.auth.service.TokenHashService;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        JwtService service = new JwtService(properties, clock);
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
        JwtService service = new JwtService(properties, Clock.systemUTC());
        UserAccount user = new UserAccount(UUID.randomUUID(), "a@example.com", "alpha", "hash",
                UserStatus.ACTIVE, "PLAYER", null, null);

        JwtService.IssuedAccessToken issuedAccess = service.issue(user, UUID.randomUUID());

        assertThatThrownBy(() -> {
            Thread.sleep(20);
            service.validate(issuedAccess.value());
        }).hasMessage("Unauthorized");
    }

    @Test
    void jwtRejectsUnexpectedAlgorithmsAndMissingRequiredClaims() throws Exception {
        KeyPair keys = TestKeys.generateRsa();
        Clock clock = Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC);
        AuthProperties properties = properties(TestKeys.privatePem(keys), TestKeys.publicPem(keys), Duration.ofMinutes(10));
        JwtService service = new JwtService(properties, clock);

        assertThatThrownBy(() -> service.validate(hs256Token(clock.instant()))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.validate(plainToken(clock.instant()))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.validate(rs256TokenWithoutClaim(keys, clock.instant(), "sid"))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.validate(rs256TokenWithoutClaim(keys, clock.instant(), "sub"))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.validate(rs256TokenWithoutClaim(keys, clock.instant(), "role"))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.validate(rs256TokenWithoutClaim(keys, clock.instant(), "jti"))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.validate(rs256TokenWithoutClaim(keys, clock.instant(), "iat"))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.validate(rs256TokenWithoutClaim(keys, clock.instant(), "exp"))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.validate(rs256TokenWithoutClaim(keys, clock.instant(), "iss"))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.validate(rs256TokenWithoutClaim(keys, clock.instant(), "aud"))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.validate(rs256Token(keys, clock.instant(), "wrong", "titancore-game-client",
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "PLAYER", 600))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.validate(rs256Token(keys, clock.instant(), "test", "wrong",
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "PLAYER", 600))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.validate(rs256Token(keys, clock.instant(), "test", "titancore-game-client",
                "not-a-uuid", UUID.randomUUID().toString(), "PLAYER", 600))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.validate(rs256Token(keys, clock.instant(), "test", "titancore-game-client",
                UUID.randomUUID().toString(), "not-a-uuid", "PLAYER", 600))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.validate(rs256Token(keys, clock.instant(), "test", "titancore-game-client",
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "SUPER_ADMIN", 600))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.validate(rs256Token(keys, clock.instant(), "test", "titancore-game-client",
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "PLAYER", -60))).isInstanceOf(AuthException.class);
    }

    @Test
    void rateLimiterFailsOpenOrClosedWhenRedisIsUnavailableByConfiguration() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.<String>any()))
                .thenThrow(new RedisConnectionFailureException("down"));

        RateLimiterService failOpen = new RateLimiterService(redisTemplate, properties(false));
        failOpen.checkLogin("127.0.0.1", "alpha@example.com");

        RateLimiterService failClosed = new RateLimiterService(redisTemplate, properties(true));
        assertThatThrownBy(() -> failClosed.checkLogin("127.0.0.1", "alpha@example.com"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Authentication temporarily unavailable");
    }

    @Test
    void loginPerformsPasswordHashWorkForUnknownAndExistingUsers() {
        UserRepository userRepository = mock(UserRepository.class);
        LoginHistoryRepository loginHistoryRepository = mock(LoginHistoryRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthService service = authService(userRepository, loginHistoryRepository, passwordEncoder);
        com.game.auth.dto.LoginRequest missing = new com.game.auth.dto.LoginRequest(
                "missing@example.com", "very-secure-password", "Browser");

        assertThatThrownBy(() -> service.login(missing, new AuthService.RequestContext("127.0.0.1", "ua")))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid credentials");
        verify(passwordEncoder).matches(org.mockito.ArgumentMatchers.eq("very-secure-password"),
                org.mockito.ArgumentMatchers.anyString());

        UserAccount user = new UserAccount(UUID.randomUUID(), "a@example.com", "alpha", "$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                UserStatus.ACTIVE, "PLAYER", null, null);
        when(userRepository.findByLogin("a@example.com")).thenReturn(java.util.Optional.of(user));
        assertThatThrownBy(() -> service.login(new com.game.auth.dto.LoginRequest(
                "a@example.com", "very-secure-password", "Browser"), new AuthService.RequestContext("127.0.0.1", "ua")))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid credentials");
        verify(passwordEncoder).matches(org.mockito.ArgumentMatchers.eq("very-secure-password"),
                org.mockito.ArgumentMatchers.eq(user.passwordHash()));
    }

    @Test
    void rateLimiterHandlesTimeoutAndScriptInfrastructureFailuresWithoutMaskingThresholds() {
        StringRedisTemplate timeoutRedis = redisFailure(new QueryTimeoutException("timeout"));
        new RateLimiterService(timeoutRedis, properties(false)).checkLogin("127.0.0.1", "alpha@example.com");
        assertThatThrownBy(() -> new RateLimiterService(timeoutRedis, properties(true))
                .checkLogin("127.0.0.1", "alpha@example.com"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Authentication temporarily unavailable");

        StringRedisTemplate scriptRedis = redisFailure(new RedisSystemException("script failed", new RuntimeException("down")));
        new RateLimiterService(scriptRedis, properties(false)).checkLogin("127.0.0.1", "alpha@example.com");
        assertThatThrownBy(() -> new RateLimiterService(scriptRedis, properties(true))
                .checkLogin("127.0.0.1", "alpha@example.com"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Authentication temporarily unavailable");

        StringRedisTemplate thresholdRedis = mock(StringRedisTemplate.class);
        when(thresholdRedis.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.<String>any()))
                .thenReturn(List.of(99L, 120_000L));
        assertThatThrownBy(() -> new RateLimiterService(thresholdRedis, properties(false))
                .checkLogin("127.0.0.1", "alpha@example.com"))
                .isInstanceOf(com.game.auth.service.RateLimitException.class);
    }

    @Test
    void clientIpResolverIgnoresForwardedHeaderUnlessRemotePeerIsTrusted() {
        HttpServletRequest direct = request("203.0.113.10", "198.51.100.55");
        assertThat(new ClientIpResolver(properties(false)).resolve(direct)).isEqualTo("203.0.113.10");

        AuthProperties trusted = properties(false, true, List.of("203.0.113.10"));
        assertThat(new ClientIpResolver(trusted).resolve(direct)).isEqualTo("198.51.100.55");
        assertThat(new ClientIpResolver(trusted).resolve(request("203.0.113.10", "2001:db8::1"))).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(new ClientIpResolver(trusted).resolve(request("203.0.113.10", "198.51.100.55, 10.0.0.1")))
                .isEqualTo("203.0.113.10");
        assertThat(new ClientIpResolver(trusted).resolve(request("203.0.113.10", "bad ip")))
                .isEqualTo("203.0.113.10");
        assertThat(new ClientIpResolver(trusted).resolve(request("203.0.113.10", "example.com")))
                .isEqualTo("203.0.113.10");
        assertThat(new ClientIpResolver(trusted).resolve(request("203.0.113.10", "198.51.100.999")))
                .isEqualTo("203.0.113.10");
        assertThat(new ClientIpResolver(trusted).resolve(request("203.0.113.10", "[2001:db8::1]")))
                .isEqualTo("203.0.113.10");
        assertThat(new ClientIpResolver(properties(false, true, List.of("203.0.113.0/24")))
                .resolve(request("203.0.113.10", "198.51.100.55"))).isEqualTo("198.51.100.55");
    }

    private AuthProperties properties(String privateKey, String publicKey, Duration accessTtl) {
        return new AuthProperties(
                new AuthProperties.Jwt("test", "titancore-game-client", privateKey, publicKey,
                        accessTtl, Duration.ofSeconds(30)),
                new AuthProperties.Refresh(Duration.ofDays(14), 48),
                new AuthProperties.Cookie("refresh_token", "/api/auth", false, "Lax"),
                new AuthProperties.RateLimit("test-secret", 20, 10, 10, 60, Duration.ofMinutes(15),
                        false, "login-history-secret"),
                new AuthProperties.TrustedProxy(false, List.of())
        );
    }

    private AuthProperties properties(boolean failClosed) {
        return properties(failClosed, false, List.of());
    }

    private AuthProperties properties(boolean failClosed, boolean trustedProxyEnabled, List<String> trustedProxies) {
        return new AuthProperties(
                new AuthProperties.Jwt("test", "titancore-game-client", "", "", Duration.ofMinutes(10), Duration.ofSeconds(30)),
                new AuthProperties.Refresh(Duration.ofDays(14), 48),
                new AuthProperties.Cookie("refresh_token", "/api/auth", false, "Lax"),
                new AuthProperties.RateLimit("test-secret", 20, 10, 10, 60, Duration.ofMinutes(15),
                        failClosed, "login-history-secret"),
                new AuthProperties.TrustedProxy(trustedProxyEnabled, trustedProxies)
        );
    }

    private StringRedisTemplate redisFailure(RuntimeException exception) {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.<String>any()))
                .thenThrow(exception);
        return redisTemplate;
    }

    private AuthService authService(UserRepository userRepository, LoginHistoryRepository loginHistoryRepository,
                                    PasswordEncoder passwordEncoder) {
        return new AuthService(
                userRepository,
                mock(PlayerFoundationRepository.class),
                mock(RefreshTokenRepository.class),
                mock(UserSessionRepository.class),
                loginHistoryRepository,
                mock(AuditLogRepository.class),
                passwordEncoder,
                mock(JwtService.class),
                mock(RefreshTokenGenerator.class),
                mock(TokenHashService.class),
                mock(RateLimiterService.class),
                properties(false),
                Clock.systemUTC()
        );
    }

    private HttpServletRequest request(String remoteAddress, String forwardedFor) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remoteAddress);
        when(request.getHeader("X-Forwarded-For")).thenReturn(forwardedFor);
        return request;
    }

    private String hs256Token(Instant now) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), standardClaims(now).build());
        jwt.sign(new MACSigner("01234567890123456789012345678901"));
        return jwt.serialize();
    }

    private String plainToken(Instant now) {
        return new PlainJWT(standardClaims(now).build()).serialize();
    }

    private String rs256TokenWithoutClaim(KeyPair keys, Instant now, String claim) throws Exception {
        JWTClaimsSet.Builder builder = standardClaims(now);
        switch (claim) {
            case "sub" -> builder.subject(null);
            case "sid" -> builder.claim("sid", null);
            case "role" -> builder.claim("role", null);
            case "jti" -> builder.jwtID(null);
            case "iat" -> builder.issueTime(null);
            case "exp" -> builder.expirationTime(null);
            case "iss" -> builder.issuer(null);
            case "aud" -> builder.audience((List<String>) null);
            default -> throw new IllegalArgumentException("Unsupported claim");
        }
        return sign(keys, builder.build());
    }

    private String rs256Token(KeyPair keys, Instant now, String issuer, String audience, String subject,
                              String sessionId, String role, long expiresInSeconds) throws Exception {
        JWTClaimsSet claims = standardClaims(now)
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .claim("sid", sessionId)
                .claim("role", role)
                .expirationTime(Date.from(now.plusSeconds(expiresInSeconds)))
                .build();
        return sign(keys, claims);
    }

    private JWTClaimsSet.Builder standardClaims(Instant now) {
        return new JWTClaimsSet.Builder()
                .issuer("test")
                .audience("titancore-game-client")
                .subject(UUID.randomUUID().toString())
                .claim("sid", UUID.randomUUID().toString())
                .claim("role", "PLAYER")
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(600)));
    }

    private String sign(KeyPair keys, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(), claims);
        jwt.sign(new com.nimbusds.jose.crypto.RSASSASigner(keys.getPrivate()));
        return jwt.serialize();
    }
}
