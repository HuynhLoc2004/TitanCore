package com.game.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.auth.config.GoogleOAuthProviderMetadata;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "logging.level.org.springframework.web.servlet.DispatcherServlet=DEBUG")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class GoogleOAuthIntegrationTests {

    private static final KeyPair TITANCORE_KEYS = TestKeys.generateRsa();
    private static final KeyPair GOOGLE_KEYS = TestKeys.generateRsa();
    private static final RSAKey GOOGLE_JWK = new RSAKey.Builder((RSAPublicKey) GOOGLE_KEYS.getPublic())
            .privateKey((RSAPrivateKey) GOOGLE_KEYS.getPrivate())
            .keyID("google-test-key")
            .build();
    private static final Map<String, TokenFixture> TOKENS = new ConcurrentHashMap<>();
    private static volatile boolean malformedTokenJson;
    private static volatile int tokenStatus = 200;
    private static volatile long tokenDelayMillis;
    private static volatile int jwksStatus = 200;
    private static HttpServer googleServer;
    private static String googleBaseUrl;

    @TestConfiguration
    static class OAuthProviderTestConfig {

        @Bean("testGoogleOAuthProviderMetadata")
        @Primary
        GoogleOAuthProviderMetadata googleOAuthProviderMetadataForTests() {
            return new GoogleOAuthProviderMetadata(
                    googleBaseUrl + "/authorize",
                    googleBaseUrl + "/token",
                    googleBaseUrl + "/jwks",
                    "https://accounts.google.com",
                    Duration.ofMinutes(5),
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(30)
            );
        }
    }

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void callbackRequestDoesNotLogSensitiveQueryParameters(CapturedOutput output) throws Exception {
        String sensitiveMarker = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/auth/oauth/google/callback")
                        .queryParam("state", sensitiveMarker)
                        .queryParam("code", sensitiveMarker))
                .andExpect(status().isFound());

        assertThat(output.getAll()).doesNotContain(sensitiveMarker);
    }

    @BeforeAll
    static void startGoogleServer() throws IOException {
        googleServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        googleBaseUrl = "http://127.0.0.1:" + googleServer.getAddress().getPort();
        googleServer.createContext("/token", exchange -> {
            if (tokenDelayMillis > 0) {
                try {
                    Thread.sleep(tokenDelayMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> form = form(body);
            TokenFixture fixture = TOKENS.get(form.get("code"));
            if (tokenStatus != 200) {
                exchange.sendResponseHeaders(tokenStatus, 0);
                exchange.close();
                return;
            }
            if (malformedTokenJson) {
                byte[] response = "{\"id_token\":".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream stream = exchange.getResponseBody()) {
                    stream.write(response);
                }
                return;
            }
            if (fixture == null || !fixture.codeVerifier().equals(form.get("code_verifier"))) {
                exchange.sendResponseHeaders(400, 0);
                exchange.close();
                return;
            }
            byte[] response = ("{\"id_token\":\"" + fixture.idToken() + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream stream = exchange.getResponseBody()) {
                stream.write(response);
            }
        });
        googleServer.createContext("/jwks", exchange -> {
            if (jwksStatus != 200) {
                exchange.sendResponseHeaders(jwksStatus, 0);
                exchange.close();
                return;
            }
            byte[] response = ("{\"keys\":[" + GOOGLE_JWK.toPublicJWK().toJSONString() + "]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream stream = exchange.getResponseBody()) {
                stream.write(response);
            }
        });
        googleServer.start();
    }

    @AfterAll
    static void stopGoogleServer() {
        if (googleServer != null) {
            googleServer.stop(0);
        }
    }

    @BeforeEach
    void clearFixtures() {
        TOKENS.clear();
        resetProviderFaults();
    }

    private static void resetProviderFaults() {
        malformedTokenJson = false;
        tokenStatus = 200;
        tokenDelayMillis = 0;
        jwksStatus = 200;
    }

    private static void setMalformedTokenJson(boolean enabled) {
        malformedTokenJson = enabled;
    }

    private static void setTokenStatus(int status) {
        tokenStatus = status;
    }

    private static void setTokenDelayMillis(long delayMillis) {
        tokenDelayMillis = delayMillis;
    }

    private static void setJwksStatus(int status) {
        jwksStatus = status;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.auth.jwt.private-key", () -> TestKeys.privatePem(TITANCORE_KEYS));
        registry.add("app.auth.jwt.public-key", () -> TestKeys.publicPem(TITANCORE_KEYS));
        registry.add("app.auth.rate-limit.login-ip-limit", () -> "50");
        registry.add("app.auth.rate-limit.login-identity-limit", () -> "50");
        registry.add("app.auth.rate-limit.register-ip-limit", () -> "50");
        registry.add("app.auth.rate-limit.refresh-limit", () -> "100");
        registry.add("app.auth.oauth.google.client-id", () -> "google-client-id");
        registry.add("app.auth.oauth.google.client-secret", () -> "google-client-secret");
        registry.add("app.auth.oauth.google.redirect-uri",
                () -> "http://localhost:8080/api/auth/oauth/google/callback");
        registry.add("app.auth.oauth.success-redirect-uri", () -> "http://localhost:5173/app");
        registry.add("app.auth.oauth.failure-redirect-uri", () -> "http://localhost:5173/login");
    }

    @Test
    void googleStartCreatesPkceStateWithoutTokensInUrl() throws Exception {
        URI location = start().location();

        assertThat(location.toString()).startsWith(googleBaseUrl + "/authorize");
        assertThat(location.toASCIIString()).contains("response_type=code")
                .contains("scope=openid%20email%20profile")
                .contains("code_challenge_method=S256")
                .doesNotContain("id_token")
                .doesNotContain("accessToken")
                .doesNotContain("refresh_token");
    }

    @Test
    void createsNewVerifiedGoogleUserSessionCookieHistoryAndAudit() throws Exception {
        OAuthStart start = start();
        prepareToken("new-code", start, "google-subject-new", "new-google@example.com", true);

        MvcResult result = callback(start.state(), "new-code").andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:5173/app?oauth=success");
        assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("access").doesNotContain("token");
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("HttpOnly")
                .contains("Path=/api/auth")
                .contains("SameSite=Lax");
        UUID userId = jdbcTemplate.queryForObject("select user_id from user_oauth_accounts where provider_subject = ?",
                UUID.class, "google-subject-new");
        assertThat(userId).isNotNull();
        assertThat(count("user_sessions", "user_id", userId)).isEqualTo(1);
        assertThat(count("refresh_tokens", "user_id", userId)).isEqualTo(1);
        assertThat(count("login_history", "user_id", userId)).isEqualTo(1);
        assertThat(count("audit_logs", "actor_user_id", userId)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void existingGoogleIdentityLogsInAndUpdatesEmailMetadata() throws Exception {
        OAuthStart firstStart = start();
        prepareToken("first-code", firstStart, "stable-google-subject", "first@example.com", true);
        callback(firstStart.state(), "first-code");

        OAuthStart secondStart = start();
        prepareToken("second-code", secondStart, "stable-google-subject", "changed@example.com", true);
        callback(secondStart.state(), "second-code").andExpect(status().isFound());

        assertThat(jdbcTemplate.queryForObject("""
                select email_at_link_time from user_oauth_accounts where provider_subject = ?
                """, String.class, "stable-google-subject")).isEqualTo("changed@example.com");
    }

    @Test
    void verifiedLocalEmailAutoLinksOnlyWhenTitanCoreEmailIsVerified() throws Exception {
        UUID verifiedUser = insertUser("verified-local@example.com", "verifiedlocal", true, "hash");
        OAuthStart verifiedStart = start();
        prepareToken("verified-code", verifiedStart, "verified-link-subject", "verified-local@example.com", true);
        callback(verifiedStart.state(), "verified-code").andExpect(status().isFound());
        assertThat(count("user_oauth_accounts", "user_id", verifiedUser)).isEqualTo(1);

        insertUser("unverified-local@example.com", "unverifiedlocal", false, "hash");
        OAuthStart unverifiedStart = start();
        prepareToken("unverified-code", unverifiedStart, "unverified-link-subject", "unverified-local@example.com", true);
        MvcResult rejected = callback(unverifiedStart.state(), "unverified-code").andReturn();
        assertThat(rejected.getResponse().getRedirectedUrl()).contains("oauth=failed").contains("code=collision");
    }

    @Test
    void rejectsInactiveAndUnverifiedGoogleAccounts() throws Exception {
        UUID userId = insertUser("inactive@example.com", "inactiveuser", true, "hash");
        jdbcTemplate.update("update users set status = 'BANNED' where id = ?", userId);
        OAuthStart inactiveStart = start();
        prepareToken("inactive-code", inactiveStart, "inactive-subject", "inactive@example.com", true);
        assertThat(callback(inactiveStart.state(), "inactive-code").andReturn().getResponse().getRedirectedUrl())
                .contains("code=inactive");

        OAuthStart unverifiedStart = start();
        prepareToken("google-unverified-code", unverifiedStart, "google-unverified-subject",
                "google-unverified@example.com", false);
        assertThat(callback(unverifiedStart.state(), "google-unverified-code").andReturn().getResponse().getRedirectedUrl())
                .contains("oauth=failed");
    }

    @Test
    void rejectsReplayedStateWrongNonceAndWrongIssuerAudienceOrSignature() throws Exception {
        OAuthStart start = start();
        prepareToken("replay-code", start, "replay-subject", "replay@example.com", true);
        callback(start.state(), "replay-code").andExpect(status().isFound());
        assertThat(callback(start.state(), "replay-code").andReturn().getResponse().getRedirectedUrl())
                .contains("code=expired");

        OAuthStart nonceStart = start();
        prepareToken("bad-nonce-code", nonceStart.withNonce("wrong"), "nonce-subject", "nonce@example.com", true);
        assertThat(callback(nonceStart.state(), "bad-nonce-code").andReturn().getResponse().getRedirectedUrl())
                .contains("oauth=failed");

        OAuthStart issuerStart = start();
        prepareToken("bad-issuer-code", issuerStart, "issuer-subject", "issuer@example.com", true,
                "https://bad-issuer.example", "google-client-id", GOOGLE_JWK);
        assertThat(callback(issuerStart.state(), "bad-issuer-code").andReturn().getResponse().getRedirectedUrl())
                .contains("oauth=failed");

        OAuthStart audienceStart = start();
        prepareToken("bad-audience-code", audienceStart, "audience-subject", "audience@example.com", true,
                "https://accounts.google.com", "wrong-client", GOOGLE_JWK);
        assertThat(callback(audienceStart.state(), "bad-audience-code").andReturn().getResponse().getRedirectedUrl())
                .contains("oauth=failed");

        OAuthStart signatureStart = start();
        KeyPair wrongPair = TestKeys.generateRsa();
        RSAKey wrongKey = new RSAKey.Builder((RSAPublicKey) wrongPair.getPublic())
                .privateKey((RSAPrivateKey) wrongPair.getPrivate())
                .keyID("google-test-key")
                .build();
        prepareToken("bad-signature-code", signatureStart, "signature-subject", "signature@example.com", true,
                "https://accounts.google.com", "google-client-id", wrongKey);
        assertThat(callback(signatureStart.state(), "bad-signature-code").andReturn().getResponse().getRedirectedUrl())
                .contains("oauth=failed");
    }

    @Test
    void rejectsMalformedOrMissingRequiredIdTokenClaimsWithoutPartialRows() throws Exception {
        assertSafeOAuthFailure("missing-iat-code", builder -> builder.issueTime(null));
        assertSafeOAuthFailure("future-iat-code", builder -> builder.issueTime(Date.from(Instant.now().plusSeconds(120))));
        assertSafeOAuthFailure("missing-audience-code", builder -> builder.audience((java.util.List<String>) null));
        assertSafeOAuthFailure("malformed-audience-code", builder -> builder.claim("aud", Map.of("bad", "audience")));
        assertSafeOAuthFailure("missing-subject-code", builder -> builder.subject(null));
        assertSafeOAuthFailure("expired-code", builder -> builder.expirationTime(Date.from(Instant.now().minusSeconds(120))));
    }

    @Test
    void rejectsUnsupportedAlgorithmWithoutPartialRows() throws Exception {
        OAuthStart start = start();
        JWTClaimsSet claims = baseClaims(start, "unsupported-alg-subject", "unsupported@example.com", true,
                "https://accounts.google.com", "google-client-id")
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("google-test-key").build(), claims);
        jwt.sign(new MACSigner("01234567890123456789012345678901"));
        TOKENS.put("unsupported-alg-code", new TokenFixture(start.codeVerifier(), jwt.serialize()));

        assertSafeFailureAndNoRows(start.state(), "unsupported-alg-code", "unsupported-alg-subject");
    }

    @Test
    void providerFailuresDoNotCreatePartialRows() throws Exception {
        OAuthStart tokenFailure = start();
        prepareToken("token-failure-code", tokenFailure, "token-failure-subject", "token-failure@example.com", true);
        setTokenStatus(500);
        assertSafeFailureAndNoRows(tokenFailure.state(), "token-failure-code", "token-failure-subject");
        setTokenStatus(200);

        OAuthStart malformedJson = start();
        prepareToken("malformed-json-code", malformedJson, "malformed-json-subject", "malformed-json@example.com", true);
        setMalformedTokenJson(true);
        assertSafeFailureAndNoRows(malformedJson.state(), "malformed-json-code", "malformed-json-subject");
        setMalformedTokenJson(false);

        OAuthStart jwksFailure = start();
        prepareToken("jwks-failure-code", jwksFailure, "jwks-failure-subject", "jwks-failure@example.com", true);
        setJwksStatus(503);
        assertSafeFailureAndNoRows(jwksFailure.state(), "jwks-failure-code", "jwks-failure-subject");
        setJwksStatus(200);

        OAuthStart timeout = start();
        prepareToken("timeout-code", timeout, "timeout-subject", "timeout@example.com", true);
        setTokenDelayMillis(3_000);
        assertSafeFailureAndNoRows(timeout.state(), "timeout-code", "timeout-subject");
    }

    @Test
    void concurrentCallbackReplayOnlySucceedsOnce() throws Exception {
        OAuthStart start = start();
        prepareToken("concurrent-replay-code", start, "concurrent-replay-subject", "concurrent@example.com", true);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<String> task = () -> callback(start.state(), "concurrent-replay-code")
                    .andReturn().getResponse().getRedirectedUrl();
            Future<String> first = executor.submit(task);
            Future<String> second = executor.submit(task);

            assertThat(Set.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .contains("http://localhost:5173/app?oauth=success")
                    .anyMatch(url -> url.contains("code=expired"));
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from user_oauth_accounts where provider_subject = ?
                """, Integer.class, "concurrent-replay-subject")).isEqualTo(1);
    }

    @Test
    void concurrentSameGoogleIdentityCreatesAtMostOneAccountAndBothReturnSafely() throws Exception {
        OAuthStart firstStart = start();
        OAuthStart secondStart = start();
        prepareToken("same-subject-code-1", firstStart, "same-race-subject", "race@example.com", true);
        prepareToken("same-subject-code-2", secondStart, "same-race-subject", "race@example.com", true);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> callback(firstStart.state(), "same-subject-code-1")
                    .andReturn().getResponse().getRedirectedUrl());
            Future<String> second = executor.submit(() -> callback(secondStart.state(), "same-subject-code-2")
                    .andReturn().getResponse().getRedirectedUrl());

            assertThat(first.get(10, TimeUnit.SECONDS)).doesNotContain("code=failed");
            assertThat(second.get(10, TimeUnit.SECONDS)).doesNotContain("code=failed");
        } finally {
            executor.shutdownNow();
        }
        UUID userId = jdbcTemplate.queryForObject("""
                select user_id from user_oauth_accounts where provider_subject = ?
                """, UUID.class, "same-race-subject");
        assertThat(count("users", "id", userId)).isEqualTo(1);
        assertThat(count("user_oauth_accounts", "user_id", userId)).isEqualTo(1);
    }

    @Test
    void providerFailureDoesNotPersistAccountOrExposeTokens() throws Exception {
        OAuthStart start = start();

        MvcResult result = callback(start.state(), "missing-code").andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).contains("oauth=failed");
        assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("code=missing-code")
                .doesNotContain("token")
                .doesNotContain("google");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from user_oauth_accounts where provider_subject = ?
                """, Integer.class, "missing-code")).isZero();
    }

    private OAuthStart start() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/oauth/google/start"))
                .andExpect(status().isFound())
                .andReturn();
        URI location = URI.create(result.getResponse().getRedirectedUrl());
        String state = query(location, "state");
        assertThat(redisTemplate.keys("auth:oauth:state:*"))
                .noneMatch(key -> key.contains(state));
        String stateJson = redisTemplate.opsForValue().get(stateKey(state));
        JsonNode json = objectMapper.readTree(stateJson);
        return new OAuthStart(location, state, json.get("nonce").asText(), json.get("codeVerifier").asText());
    }

    private org.springframework.test.web.servlet.ResultActions callback(String state, String code) throws Exception {
        return mockMvc.perform(get("/api/auth/oauth/google/callback")
                .queryParam("state", state)
                .queryParam("code", code));
    }

    private void prepareToken(String code, OAuthStart start, String subject, String email, boolean emailVerified)
            throws Exception {
        prepareToken(code, start, subject, email, emailVerified,
                "https://accounts.google.com", "google-client-id", GOOGLE_JWK);
    }

    private void prepareToken(String code, OAuthStart start, String subject, String email, boolean emailVerified,
                              String issuer, String audience, RSAKey key) throws Exception {
        JWTClaimsSet claims = baseClaims(start, subject, email, emailVerified, issuer, audience).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("google-test-key").build(), claims);
        jwt.sign(new RSASSASigner(key));
        TOKENS.put(code, new TokenFixture(start.codeVerifier(), jwt.serialize()));
    }

    private JWTClaimsSet.Builder baseClaims(OAuthStart start, String subject, String email, boolean emailVerified,
                                            String issuer, String audience) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .claim("nonce", start.nonce())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .issueTime(Date.from(Instant.now()));
    }

    private void assertSafeOAuthFailure(String code, Consumer<JWTClaimsSet.Builder> customizer) throws Exception {
        OAuthStart start = start();
        String subject = code + "-subject";
        JWTClaimsSet.Builder builder = baseClaims(start, subject, code + "@example.com", true,
                "https://accounts.google.com", "google-client-id");
        customizer.accept(builder);
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("google-test-key").build(),
                builder.build());
        jwt.sign(new RSASSASigner(GOOGLE_JWK));
        TOKENS.put(code, new TokenFixture(start.codeVerifier(), jwt.serialize()));

        assertSafeFailureAndNoRows(start.state(), code, subject);
    }

    private void assertSafeFailureAndNoRows(String state, String code, String subject) throws Exception {
        int userCount = count("users");
        int oauthCount = count("user_oauth_accounts");
        int profileCount = count("player_profiles");
        int sessionCount = count("user_sessions");
        int tokenCount = count("refresh_tokens");

        MvcResult result = callback(state, code).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getRedirectedUrl()).contains("oauth=failed")
                .doesNotContain(code)
                .doesNotContain("token")
                .doesNotContain("google");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from user_oauth_accounts where provider_subject = ?
                """, Integer.class, subject)).isZero();
        assertThat(count("users")).isEqualTo(userCount);
        assertThat(count("user_oauth_accounts")).isEqualTo(oauthCount);
        assertThat(count("player_profiles")).isEqualTo(profileCount);
        assertThat(count("user_sessions")).isEqualTo(sessionCount);
        assertThat(count("refresh_tokens")).isEqualTo(tokenCount);
    }

    private UUID insertUser(String email, String username, boolean verified, String passwordHash) {
        return jdbcTemplate.queryForObject("""
                insert into users (email, username, password_hash, email_verified_at)
                values (?, ?, ?, case when ? then now() else null end)
                returning id
                """, UUID.class, email, username, passwordHash, verified);
    }

    private int count(String table, String column, UUID id) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + column + " = ?", Integer.class, id);
        return count == null ? 0 : count;
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private static String query(URI uri, String name) {
        return form(uri.getRawQuery()).get(name);
    }

    private static Map<String, String> form(String body) {
        Map<String, String> values = new java.util.HashMap<>();
        if (body == null || body.isBlank()) {
            return values;
        }
        for (String part : body.split("&")) {
            String[] pieces = part.split("=", 2);
            values.put(decode(pieces[0]), pieces.length == 2 ? decode(pieces[1]) : "");
        }
        return values;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String stateKey(String state) throws Exception {
        return "auth:oauth:state:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(state.getBytes(StandardCharsets.UTF_8)));
    }

    private record OAuthStart(URI location, String state, String nonce, String codeVerifier) {
        OAuthStart withNonce(String nextNonce) {
            return new OAuthStart(location, state, nextNonce, codeVerifier);
        }
    }

    private record TokenFixture(String codeVerifier, String idToken) {
    }
}
