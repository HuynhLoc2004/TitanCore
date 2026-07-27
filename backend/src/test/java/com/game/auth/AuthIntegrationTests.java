package com.game.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.auth.service.AuthException;
import com.game.player.dto.CompleteOnboardingRequest;
import com.game.player.service.PlayerProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.KeyPair;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "VA_FORMAT_STRING_USES_NEWLINE",
        justification = "Readable JSON text blocks are intentional in MockMvc fixtures")
class AuthIntegrationTests {

    private static final KeyPair KEYS = TestKeys.generateRsa();

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

    @Autowired
    private PlayerProfileService playerProfileService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.auth.jwt.private-key", () -> TestKeys.privatePem(KEYS));
        registry.add("app.auth.jwt.public-key", () -> TestKeys.publicPem(KEYS));
        registry.add("app.auth.rate-limit.login-ip-limit", () -> "50");
        registry.add("app.auth.rate-limit.login-identity-limit", () -> "3");
        registry.add("app.auth.rate-limit.register-ip-limit", () -> "50");
        registry.add("app.auth.rate-limit.window", () -> "2m");
        registry.add("app.auth.trusted-proxy.enabled", () -> "false");
        registry.add("app.cors.allowed-origins", () -> "http://localhost:5173");
    }

    @Test
    void lobbyBootstrapRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/lobby/bootstrap"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_PROBLEM_JSON_VALUE
                ));
    }

    @Test
    void lobbyBootstrapRequiresOnboardingAndNeverExposesInternalIdentity()
            throws Exception {
        AuthResult auth = register("lobby-user@example.com", "lobbyinternal");
        MockHttpServletRequestBuilder request = get("/api/lobby/bootstrap")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.accessToken())
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US");

        MvcResult incomplete = mockMvc.perform(request)
                .andExpect(status().isConflict())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_PROBLEM_JSON_VALUE
                ))
                .andReturn();
        assertThat(incomplete.getResponse().getContentAsString())
                .contains("PROFILE_ONBOARDING_REQUIRED")
                .doesNotContain("lobbyinternal", "lobby-user@example.com");

        playerProfileService.completeOnboarding(
                UUID.fromString(auth.userId()),
                new CompleteOnboardingRequest("Lobby Titan", 0),
                "127.0.0.1"
        );
        MvcResult completed = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                ))
                .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "en-US"))
                .andExpect(header().string(HttpHeaders.ETAG,
                        org.hamcrest.Matchers.startsWith("W/\"")))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "private, no-cache, must-revalidate"
                ))
                .andExpect(header().string(
                        HttpHeaders.VARY,
                        "Authorization, Accept-Language, Origin"
                ))
                .andReturn();
        assertThat(completed.getResponse().getContentAsString())
                .contains("Lobby Titan")
                .doesNotContain("lobbyinternal", "lobby-user@example.com", "object_key");

        String etag = Objects.requireNonNull(
                completed.getResponse().getHeader(HttpHeaders.ETAG));
        mockMvc.perform(request.header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag))
                .andExpect(header().string(
                        HttpHeaders.VARY,
                        "Authorization, Accept-Language, Origin"
                ))
                .andExpect(content().string(""));
    }

    @Test
    void lobbyBootstrapRejectsRevokedSessionAndInactiveAccount() throws Exception {
        AuthResult revoked = register("lobby-revoked@example.com", "lobbyrevoked");
        playerProfileService.completeOnboarding(
                UUID.fromString(revoked.userId()),
                new CompleteOnboardingRequest("Revoked Titan", 0),
                "127.0.0.1"
        );
        UUID sessionId = firstSessionId(revoked.accessToken());
        jdbcTemplate.update("""
                update user_sessions
                set ended_at = now()
                where id = ?
                """, sessionId);

        mockMvc.perform(get("/api/lobby/bootstrap")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(revoked.accessToken())))
                .andExpect(status().isUnauthorized());

        AuthResult inactive = register("lobby-banned@example.com", "lobbybanned");
        jdbcTemplate.update(
                "update users set status = 'BANNED' where id = ?",
                UUID.fromString(inactive.userId())
        );

        mockMvc.perform(get("/api/lobby/bootstrap")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(inactive.accessToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationCreatesUserPlayerFoundationSessionAndCookie() throws Exception {
        AuthResult auth = register("alpha@example.com", "alphauser");

        assertThat(auth.accessToken()).isNotBlank();
        assertThat(auth.refreshCookie()).contains("HttpOnly").contains("Path=/api/auth").contains("SameSite=Lax");
        UUID userId = UUID.fromString(auth.userId());
        UUID playerId = jdbcTemplate.queryForObject(
                "select id from player_profiles where user_id = ?", UUID.class, userId);
        assertThat(playerId).isNotNull();
        assertThat(count("player_statistics", "player_id", playerId)).isEqualTo(1);
        assertThat(count("player_settings", "player_id", playerId)).isEqualTo(1);
        assertThat(count("inventories", "player_id", playerId)).isEqualTo(1);
        assertThat(count("user_sessions", "user_id", userId)).isEqualTo(1);
        assertThat(count("refresh_tokens", "user_id", userId)).isEqualTo(1);
        MvcResult me = mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(auth.accessToken())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode user = objectMapper.readTree(me.getResponse().getContentAsString());
        assertThat(user.has("username")).isFalse();
        assertThat(user.path("profile").path("displayName").isNull()).isTrue();
        assertThat(user.path("profile").path("onboardingStatus").asText()).isEqualTo("REQUIRED");
    }

    @Test
    void completesOnboardingWithNormalizationIdempotencyAndOptimisticLocking() throws Exception {
        AuthResult auth = register("onboarding@example.com", "internaluser");

        MvcResult completed = mockMvc.perform(withCsrf(post("/api/player/profile/onboarding"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"  Ra\u0301id\u2002 Hu\u0300ng  ","expectedVersion":0}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode profile = objectMapper.readTree(completed.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(profile.path("displayName").asText()).isEqualTo("R\u00E1id H\u00F9ng");
        assertThat(profile.path("onboardingStatus").asText()).isEqualTo("COMPLETED");
        assertThat(profile.path("version").asLong()).isEqualTo(1);
        UUID userId = UUID.fromString(auth.userId());
        assertThat(count("audit_logs", "actor_user_id", userId)).isEqualTo(2);

        mockMvc.perform(withCsrf(post("/api/player/profile/onboarding"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"R\u00C1ID H\u00D9NG","expectedVersion":0}
                                """))
                .andExpect(status().isOk());
        assertThat(count("audit_logs", "actor_user_id", userId)).isEqualTo(2);

        mockMvc.perform(withCsrf(post("/api/player/profile/onboarding"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Different Hero","expectedVersion":1}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsDuplicateDisplayNamesCsrfAndInactiveAccounts() throws Exception {
        AuthResult first = register("first-profile@example.com", "firstprofile");
        AuthResult second = register("second-profile@example.com", "secondprofile");
        completeOnboarding(first, "Raid Hero", 0);

        mockMvc.perform(withCsrf(post("/api/player/profile/onboarding"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(second.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"RAID HERO","expectedVersion":0}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/player/profile/onboarding")
                        .header(HttpHeaders.AUTHORIZATION, bearer(second.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Other Hero","expectedVersion":0}
                                """))
                .andExpect(status().isForbidden());

        jdbcTemplate.update("update users set status = 'LOCKED' where id = ?", UUID.fromString(second.userId()));
        mockMvc.perform(withCsrf(post("/api/player/profile/onboarding"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(second.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Other Hero","expectedVersion":0}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentIdenticalOnboardingIsLogicallyIdempotentAndAuditedOnce() throws Exception {
        AuthResult auth = register("same-race@example.com", "samerace");
        UUID userId = UUID.fromString(auth.userId());
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> completion = () -> {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent onboarding test did not start");
            }
            return playerProfileService.completeOnboarding(
                    userId,
                    new CompleteOnboardingRequest("Same Race Hero", 0),
                    "203.0.113.31"
            ).onboardingStatus();
        };
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(completion);
            Future<String> second = executor.submit(completion);
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("COMPLETED", "COMPLETED");
        } finally {
            executor.shutdownNow();
        }

        assertThat(profileVersion(userId)).isEqualTo(1);
        assertThat(onboardingAuditCount(userId)).isEqualTo(1);
        assertThat(profileDisplayName(userId)).isEqualTo("Same Race Hero");
    }

    @Test
    void differentUsersRacingForCanonicalNameHaveOneWinnerAndNoPartialState() throws Exception {
        AuthResult firstAuth = register("name-race-one@example.com", "nameraceone");
        AuthResult secondAuth = register("name-race-two@example.com", "nameracetwo");
        UUID firstUserId = UUID.fromString(firstAuth.userId());
        UUID secondUserId = UUID.fromString(secondAuth.userId());
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> first = onboardingRaceCall(firstUserId, start, "203.0.113.41");
        Callable<String> second = onboardingRaceCall(secondUserId, start, "203.0.113.42");
        var executor = Executors.newFixedThreadPool(2);
        List<String> results;
        try {
            Future<String> firstResult = executor.submit(first);
            Future<String> secondResult = executor.submit(second);
            start.countDown();
            results = List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertThat(results).containsExactlyInAnyOrder("COMPLETED", "DISPLAY_NAME_UNAVAILABLE");
        assertThat(List.of(profileVersion(firstUserId), profileVersion(secondUserId)))
                .containsExactlyInAnyOrder(0L, 1L);
        assertThat(onboardingAuditCount(firstUserId) + onboardingAuditCount(secondUserId)).isEqualTo(1);
        assertThat(completedProfileCount(firstUserId, secondUserId)).isEqualTo(1);
    }

    @Test
    void auditFailureRollsBackEveryProfileMutationAndLeavesNoFalseSuccess() throws Exception {
        AuthResult auth = register("audit-rollback@example.com", "auditrollback");
        UUID userId = UUID.fromString(auth.userId());
        ProfileState before = profileState(userId);
        jdbcTemplate.execute("""
                create function test_reject_onboarding_audit() returns trigger
                language plpgsql as $$
                begin
                    if new.action = 'PLAYER_ONBOARDING_COMPLETED' then
                        raise exception 'forced onboarding audit failure';
                    end if;
                    return new;
                end
                $$
                """);
        jdbcTemplate.execute("""
                create trigger test_reject_onboarding_audit_trigger
                before insert on audit_logs
                for each row execute function test_reject_onboarding_audit()
                """);
        try {
            assertThatThrownBy(() -> playerProfileService.completeOnboarding(
                    userId,
                    new CompleteOnboardingRequest("Rollback Hero", 0),
                    "203.0.113.51"
            )).isInstanceOf(org.springframework.dao.DataAccessException.class);

            assertThat(profileState(userId)).isEqualTo(before);
            assertThat(onboardingAuditCount(userId)).isZero();
        } finally {
            jdbcTemplate.execute("drop trigger if exists test_reject_onboarding_audit_trigger on audit_logs");
            jdbcTemplate.execute("drop function if exists test_reject_onboarding_audit()");
        }
    }

    @Test
    void duplicateCaseInsensitiveEmailAndUsernameAreRejectedWithoutPartialFoundation() throws Exception {
        register("duplicate@example.com", "duplicate");

        mockMvc.perform(withCsrf(post("/api/auth/register"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "DUPLICATE@example.com",
                                  "username": "othername",
                                  "password": "very-secure-password",
                                  "deviceLabel": "Browser"
                                }
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(withCsrf(post("/api/auth/register"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "other@example.com",
                                  "username": "DUPLICATE",
                                  "password": "very-secure-password",
                                  "deviceLabel": "Browser"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void loginRefreshRotationReuseLogoutAndSessionOwnershipWork() throws Exception {
        register("bravo@example.com", "bravouser");
        AuthResult login = login("bravo@example.com");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(login.accessToken())))
                .andExpect(status().isOk());

        CsrfMaterial refreshCsrf = csrf();
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(cookie(login.refreshCookie()))
                        .cookie(refreshCsrf.cookie())
                        .header("X-XSRF-TOKEN", refreshCsrf.value()))
                .andExpect(status().isOk())
                .andReturn();
        AuthResult refreshed = authResult(refreshResult);
        assertThat(activeRefreshTokenCount(UUID.fromString(refreshed.userId()))).isEqualTo(2);

        CsrfMaterial replayCsrf = csrf();
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(cookie(login.refreshCookie()))
                        .cookie(replayCsrf.cookie())
                        .header("X-XSRF-TOKEN", replayCsrf.value()))
                .andExpect(status().isUnauthorized());
        assertThat(activeRefreshTokenCount(UUID.fromString(refreshed.userId()))).isEqualTo(1);

        AuthResult secondLogin = login("bravo@example.com");

        mockMvc.perform(get("/api/auth/sessions").header(HttpHeaders.AUTHORIZATION, bearer(secondLogin.accessToken())))
                .andExpect(status().isOk());

        CsrfMaterial logoutCsrf = csrf();
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondLogin.accessToken()))
                        .cookie(logoutCsrf.cookie())
                        .header("X-XSRF-TOKEN", logoutCsrf.value()))
                .andExpect(status().isNoContent());
    }

    @Test
    void bannedLockedAndDeletedUsersCannotLogin() throws Exception {
        register("status@example.com", "statususer");
        List<String> statuses = List.of("BANNED", "LOCKED", "DELETED");
        for (String accountStatus : statuses) {
            jdbcTemplate.update("update users set status = ? where email = ?", accountStatus, "status@example.com");
            mockMvc.perform(withCsrf(post("/api/auth/login"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "login": "status@example.com",
                                      "password": "very-secure-password",
                                      "deviceLabel": "Browser"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void rateLimitUsesPrivateRedisKeysAndReturnsRetryAfter() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(withCsrf(post("/api/auth/login"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "login": "missing@example.com",
                                      "password": "very-secure-password",
                                      "deviceLabel": "Browser"
                                    }
                                    """))
                    .andExpect(i < 3 ? status().isUnauthorized() : status().isTooManyRequests());
        }
        List<String> keys = redisKeys();
        assertThat(keys).allMatch(key -> key.startsWith("auth:rate:"));
        assertThat(keys).noneMatch(key -> key.contains("missing@example.com"));
        assertThat(keys).allMatch(key -> {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return ttl != null && ttl > 0;
        });
    }

    @Test
    void csrfAndCorsAreEnforcedForCookieMutationEndpoints() throws Exception {
        AuthResult auth = register("csrf@example.com", "csrfuser");
        CsrfMaterial csrf = csrf();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("csrf@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(result -> assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                        .noneMatch(cookie -> cookie.startsWith("refresh_token=")));
        mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("csrf@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(result -> assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                        .noneMatch(cookie -> cookie.startsWith("refresh_token=")));
        mockMvc.perform(withCsrf(post("/api/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("csrf@example.com")))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));

        mockMvc.perform(post("/api/auth/refresh").cookie(cookie(auth.refreshCookie())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(cookie(auth.refreshCookie()))
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", "wrong"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(cookie(auth.refreshCookie()))
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.value()))
                .andExpect(status().isOk());

        mockMvc.perform(options("/api/auth/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));

        mockMvc.perform(options("/api/auth/me")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("csrf@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(result -> assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                        .noneMatch(cookie -> cookie.startsWith("refresh_token=")));
    }

    @Test
    void concurrentRefreshAllowsOnlyOneRotationAndReplayRevokesFamily() throws Exception {
        register("concurrent@example.com", "concurrent");
        AuthResult login = login("concurrent@example.com");
        CountDownLatch latch = new CountDownLatch(1);
        Callable<Integer> refreshCall = () -> {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent refresh test did not start");
            }
            CsrfMaterial csrf = csrf();
            return mockMvc.perform(post("/api/auth/refresh")
                            .cookie(cookie(login.refreshCookie()))
                            .cookie(csrf.cookie())
                            .header("X-XSRF-TOKEN", csrf.value()))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(refreshCall);
            var second = executor.submit(refreshCall);
            latch.countDown();
            List<Integer> statuses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertThat(statuses).contains(200);
            assertThat(statuses).contains(401);
        } finally {
            executor.shutdownNow();
        }
        assertThat(activeRefreshTokenCount(UUID.fromString(login.userId()))).isEqualTo(1);
    }

    @Test
    void authenticatedRequestsRejectInactiveAccountsAndSessionIdor() throws Exception {
        AuthResult owner = register("owner@example.com", "owneruser");
        AuthResult other = register("other@example.com", "otheruser");
        UUID otherSessionId = firstSessionId(other.accessToken());

        CsrfMaterial deleteCsrf = csrf();
        mockMvc.perform(delete("/api/auth/sessions/" + otherSessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
                        .cookie(deleteCsrf.cookie())
                        .header("X-XSRF-TOKEN", deleteCsrf.value()))
                .andExpect(status().isNotFound());

        jdbcTemplate.update("update users set status = ? where id = ?", "BANNED", UUID.fromString(owner.userId()));
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void untrustedForwardedForCannotSpoofRateLimitOrAuditIp() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(withCsrf(post("/api/auth/login"))
                            .with(request -> {
                                request.setRemoteAddr("203.0.113.77");
                                return request;
                            })
                            .header("X-Forwarded-For", "198.51.100." + i)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "login": "spoof@example.com",
                                      "password": "very-secure-password",
                                      "deviceLabel": "Browser"
                                    }
                                    """))
                    .andExpect(i < 3 ? status().isUnauthorized() : status().isTooManyRequests());
        }
        assertThat(redisKeys()).noneMatch(key -> key.contains("198.51.100."));
    }

    private AuthResult register(String email, String username) throws Exception {
        MvcResult result = mockMvc.perform(withCsrf(post("/api/auth/register"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "username": "%s",
                                  "password": "very-secure-password",
                                  "deviceLabel": "Browser"
                                }
                                """.formatted(email, username)))
                .andExpect(status().isOk())
                .andReturn();
        return authResult(result);
    }

    private AuthResult login(String login) throws Exception {
        MvcResult result = mockMvc.perform(withCsrf(post("/api/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(login)))
                .andExpect(status().isOk())
                .andReturn();
        return authResult(result);
    }

    private void completeOnboarding(AuthResult auth, String displayName, long expectedVersion) throws Exception {
        mockMvc.perform(withCsrf(post("/api/player/profile/onboarding"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(auth.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"%s","expectedVersion":%d}
                                """.formatted(displayName, expectedVersion)))
                .andExpect(status().isOk());
    }

    private Callable<String> onboardingRaceCall(UUID userId, CountDownLatch start, String ipAddress) {
        return () -> {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent onboarding test did not start");
            }
            try {
                return playerProfileService.completeOnboarding(
                        userId,
                        new CompleteOnboardingRequest("Shared Race Hero", 0),
                        ipAddress
                ).onboardingStatus();
            } catch (AuthException exception) {
                return exception.code();
            }
        };
    }

    private long profileVersion(UUID userId) {
        Long version = jdbcTemplate.queryForObject(
                "select version from player_profiles where user_id = ?", Long.class, userId);
        return version == null ? -1 : version;
    }

    private String profileDisplayName(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select display_name from player_profiles where user_id = ?", String.class, userId);
    }

    private int onboardingAuditCount(UUID userId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from audit_logs
                where actor_user_id = ? and action = 'PLAYER_ONBOARDING_COMPLETED'
                """, Integer.class, userId);
        return count == null ? 0 : count;
    }

    private int completedProfileCount(UUID firstUserId, UUID secondUserId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from player_profiles
                where user_id in (?, ?) and onboarding_completed_at is not null
                """, Integer.class, firstUserId, secondUserId);
        return count == null ? 0 : count;
    }

    private ProfileState profileState(UUID userId) {
        return jdbcTemplate.queryForObject("""
                select display_name, display_name_key, onboarding_completed_at, version
                from player_profiles where user_id = ?
                """, (resultSet, rowNumber) -> new ProfileState(
                        resultSet.getString("display_name"),
                        resultSet.getString("display_name_key"),
                        resultSet.getTimestamp("onboarding_completed_at"),
                        resultSet.getLong("version")
                ), userId);
    }

    private String loginJson(String login) {
        return """
                {
                  "login": "%s",
                  "password": "very-secure-password",
                  "deviceLabel": "Browser"
                }
                """.formatted(login);
    }

    private AuthResult authResult(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthResult(
                json.get("accessToken").asText(),
                json.get("user").get("id").asText(),
                result.getResponse().getHeader(HttpHeaders.SET_COOKIE)
        );
    }

    private jakarta.servlet.http.Cookie cookie(String setCookie) {
        String[] parts = setCookie.split(";", 2)[0].split("=", 2);
        return new jakarta.servlet.http.Cookie(parts[0], parts[1]);
    }

    private CsrfMaterial csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        jakarta.servlet.http.Cookie cookie = cookie(setCookie);
        return new CsrfMaterial(cookie, cookie.getValue());
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) throws Exception {
        CsrfMaterial csrf = csrf();
        return builder.cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.value());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private int count(String table, String column, UUID id) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + column + " = ?", Integer.class, id);
        return count == null ? 0 : count;
    }

    private int activeRefreshTokenCount(UUID userId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from refresh_tokens
                where user_id = ?
                and revoked_at is null
                and replaced_by_token_id is null
                """, Integer.class, userId);
        return count == null ? 0 : count;
    }

    private List<String> redisKeys() {
        java.util.Set<String> keys = redisTemplate.keys("auth:rate:*");
        return keys == null ? List.of() : keys.stream().toList();
    }

    private UUID firstSessionId(String access) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/sessions").header(HttpHeaders.AUTHORIZATION, bearer(access)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode sessions = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(sessions.get(0).get("id").asText());
    }

    private record AuthResult(String accessToken, String userId, String refreshCookie) {
    }

    private record CsrfMaterial(jakarta.servlet.http.Cookie cookie, String value) {
    }

    private record ProfileState(String displayName, String displayNameKey,
                                java.sql.Timestamp onboardingCompletedAt, long version) {
    }
}
