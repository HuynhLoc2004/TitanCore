package com.game.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class GoogleOAuthIntegrationTests {

    private static final KeyPair TITANCORE_KEYS = TestKeys.generateRsa();
    private static final KeyPair GOOGLE_KEYS = TestKeys.generateRsa();
    private static final RSAKey GOOGLE_JWK = new RSAKey.Builder((RSAPublicKey) GOOGLE_KEYS.getPublic())
            .privateKey((RSAPrivateKey) GOOGLE_KEYS.getPrivate())
            .keyID("google-test-key")
            .build();
    private static final Map<String, TokenFixture> TOKENS = new ConcurrentHashMap<>();
    private static HttpServer googleServer;
    private static String googleBaseUrl;

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

    @BeforeAll
    static void startGoogleServer() throws IOException {
        googleServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        googleBaseUrl = "http://127.0.0.1:" + googleServer.getAddress().getPort();
        googleServer.createContext("/token", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> form = form(body);
            TokenFixture fixture = TOKENS.get(form.get("code"));
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
        registry.add("app.auth.oauth.google.authorization-uri", () -> googleBaseUrl + "/authorize");
        registry.add("app.auth.oauth.google.token-uri", () -> googleBaseUrl + "/token");
        registry.add("app.auth.oauth.google.jwks-uri", () -> googleBaseUrl + "/jwks");
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
        String stateJson = redisTemplate.opsForValue().get("auth:oauth:state:" + state);
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
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .claim("nonce", start.nonce())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .issueTime(Date.from(Instant.now()))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("google-test-key").build(), claims);
        jwt.sign(new RSASSASigner(key));
        TOKENS.put(code, new TokenFixture(start.codeVerifier(), jwt.serialize()));
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

    private record OAuthStart(URI location, String state, String nonce, String codeVerifier) {
        OAuthStart withNonce(String nextNonce) {
            return new OAuthStart(location, state, nextNonce, codeVerifier);
        }
    }

    private record TokenFixture(String codeVerifier, String idToken) {
    }
}
