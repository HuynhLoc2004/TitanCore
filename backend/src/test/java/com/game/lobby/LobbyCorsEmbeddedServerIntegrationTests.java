package com.game.lobby;

import com.game.auth.TestKeys;
import com.game.auth.model.UserAccount;
import com.game.auth.model.UserStatus;
import com.game.auth.service.JwtService;
import com.game.lobby.dto.LobbyBootstrapResponse;
import com.game.lobby.dto.LobbySectionResponse;
import com.game.lobby.service.LobbyBootstrapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LobbyCorsEmbeddedServerIntegrationTests {

    private static final KeyPair KEYS = TestKeys.generateRsa();
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String ETAG = "W/\"" + "a".repeat(64) + "\"";
    private static final String RATE_LIMIT_TEST_KEY =
            UUID.randomUUID().toString() + UUID.randomUUID();
    private static final String LOGIN_HISTORY_TEST_KEY =
            UUID.randomUUID().toString() + UUID.randomUUID();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private LobbyBootstrapService lobbyBootstrapService;

    private String accessToken;
    private int maximumResponseBytes;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.auth.jwt.private-key", () -> TestKeys.privatePem(KEYS));
        registry.add("app.auth.jwt.public-key", () -> TestKeys.publicPem(KEYS));
        registry.add("app.auth.rate-limit.key-secret",
                () -> RATE_LIMIT_TEST_KEY);
        registry.add("app.auth.rate-limit.login-history-key-secret",
                () -> LOGIN_HISTORY_TEST_KEY);
        registry.add("app.auth.trusted-proxy.enabled", () -> "false");
        registry.add("app.cors.allowed-origins", () -> ALLOWED_ORIGIN);
    }

    @BeforeEach
    void createAuthenticatedSessionAndLargeResponse() {
        UUID userId = jdbcTemplate.queryForObject("""
                insert into users (email, username, password_hash)
                values (?, ?, 'test-hash')
                returning id
                """, UUID.class, UUID.randomUUID() + "@example.com",
                "cors" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        UUID sessionId = jdbcTemplate.queryForObject("""
                insert into user_sessions (
                    user_id, session_key, device_label, ip_address,
                    started_at, last_seen_at
                ) values (?, ?, 'CORS test', '127.0.0.1', now(), now())
                returning id
                """, UUID.class, userId, "cors-session-" + UUID.randomUUID());
        UserAccount user = new UserAccount(
                userId,
                "not-returned@example.com",
                "not-returned",
                "test-hash",
                UserStatus.ACTIVE,
                "PLAYER",
                null,
                null
        );
        accessToken = jwtService.issue(user, sessionId).value();
        LobbyBootstrapResponse response = largeResponse();
        maximumResponseBytes = serializedSizeEstimate(response);
        assertThat(maximumResponseBytes).isGreaterThan(8 * 1024);
        when(lobbyBootstrapService.bootstrap(eq(userId), any()))
                .thenReturn(new LobbyBootstrapService.BootstrapResult(
                        response,
                        ETAG,
                        false
                ));
    }

    @Test
    void preservesCorsAndLobbyVaryTokensBeforeLargeResponseCommitAndOn304()
            throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        URI endpoint = URI.create("http://localhost:" + port + "/api/lobby/bootstrap");

        HttpResponse<String> preflight = client.send(
                HttpRequest.newBuilder(endpoint)
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                HttpHeaders.AUTHORIZATION)
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(preflight.statusCode()).isEqualTo(200);
        assertVary(
                preflight,
                HttpHeaders.ORIGIN,
                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.ACCEPT_LANGUAGE
        );

        HttpResponse<String> ok = client.send(
                authenticatedRequest(endpoint).build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(ok.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isGreaterThan(8 * 1024)
                .isLessThanOrEqualTo(LobbyBootstrapService.MAX_RESPONSE_BYTES);
        assertVary(
                ok,
                HttpHeaders.ORIGIN,
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.ACCEPT_LANGUAGE
        );

        HttpResponse<String> notModified = client.send(
                authenticatedRequest(endpoint)
                        .header(HttpHeaders.IF_NONE_MATCH, ETAG)
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(notModified.statusCode()).isEqualTo(304);
        assertThat(notModified.body()).isEmpty();
        assertVary(
                notModified,
                HttpHeaders.ORIGIN,
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.ACCEPT_LANGUAGE
        );
        System.out.printf(
                "lobby-embedded-http responseBytes=%d preflight=%d get=%d notModified=%d%n",
                maximumResponseBytes,
                preflight.statusCode(),
                ok.statusCode(),
                notModified.statusCode()
        );
    }

    private HttpRequest.Builder authenticatedRequest(URI endpoint) {
        return HttpRequest.newBuilder(endpoint)
                .GET()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "vi-VN")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN);
    }

    private void assertVary(HttpResponse<?> response, String... expected) {
        List<String> rawHeaders = response.headers().allValues(HttpHeaders.VARY);
        List<String> tokens = rawHeaders.stream()
                .flatMap(value -> List.of(value.split(",")).stream())
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
        assertThat(tokens).doesNotContain("*");
        assertThat(new HashSet<>(tokens)).hasSize(tokens.size());
        for (String varyToken : expected) {
            assertThat(tokens).contains(varyToken.toLowerCase(Locale.ROOT));
        }
    }

    private LobbyBootstrapResponse largeResponse() {
        List<LobbySectionResponse> sections = new ArrayList<>();
        int remainingAssets = 24;
        for (int index = 0; index < 7; index++) {
            int sectionAssets = Math.min(4, remainingAssets);
            remainingAssets -= sectionAssets;
            List<LobbySectionResponse.AssetDescriptor> assets = new ArrayList<>();
            for (int assetIndex = 0; assetIndex < sectionAssets; assetIndex++) {
                assets.add(new LobbySectionResponse.AssetDescriptor(
                        UUID.randomUUID().toString(),
                        "HERO",
                        "image/webp",
                        1920,
                        1080,
                        null,
                        "b".repeat(64),
                        null
                ));
            }
            sections.add(new LobbySectionResponse.EventSpotlight(
                    "event-" + index,
                    "EVENT_SPOTLIGHT",
                    index,
                    "T".repeat(96),
                    "C".repeat(500),
                    "INFO",
                    new LobbySectionResponse.ContentRef(
                            UUID.randomUUID().toString(),
                            UUID.randomUUID().toString(),
                            1,
                            "c".repeat(64)
                    ),
                    assets
            ));
        }
        return new LobbyBootstrapResponse(
                1,
                Instant.parse("2026-07-27T06:00:00Z"),
                "vi-VN",
                new LobbyBootstrapResponse.PlayerSummary("CORS Titan", 1),
                List.of(),
                sections,
                null,
                new LobbyBootstrapResponse.DegradedState(false, List.of())
        );
    }

    private int serializedSizeEstimate(LobbyBootstrapResponse response) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .findAndRegisterModules()
                    .writeValueAsBytes(response)
                    .length;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
