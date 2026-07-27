package com.game.lobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.auth.dto.ProfileIdentityResponse;
import com.game.auth.security.AuthenticatedUser;
import com.game.lobby.controller.LobbyBootstrapController;
import com.game.lobby.dto.LobbyBootstrapResponse;
import com.game.lobby.model.ResolvedLobbyContent;
import com.game.lobby.registry.LobbyComponentRegistry;
import com.game.lobby.repository.LobbyContentReadRepository;
import com.game.lobby.service.LobbyBootstrapException;
import com.game.lobby.service.LobbyBootstrapService;
import com.game.lobby.service.LobbyEtagBuilder;
import com.game.lobby.service.LobbyLocaleResolver;
import com.game.player.service.PlayerProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

class LobbyBootstrapServiceAndControllerTests {

    private static final Instant NOW = Instant.parse("2026-07-27T06:00:00Z");
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().findAndRegisterModules();

    @Test
    void completedProfileReturnsBoundedPublicIdentityAndEmptyContent() throws Exception {
        Fixture fixture = fixture(new ProfileIdentityResponse(
                UUID.randomUUID(), "Loc Titan", "COMPLETED", 4));
        when(fixture.repository().resolve("vi-VN"))
                .thenReturn(new ResolvedLobbyContent(
                        NOW, null, false, List.of()));
        when(fixture.repository().findAssets(List.of())).thenReturn(List.of());

        LobbyBootstrapService.BootstrapResult result =
                fixture.service().bootstrap(UUID.randomUUID(), "vi-VN");
        String json = OBJECT_MAPPER.writeValueAsString(result.response());

        assertThat(result.response().player().displayName()).isEqualTo("Loc Titan");
        assertThat(result.response().navigation()).isEmpty();
        assertThat(result.response().sections()).isEmpty();
        assertThat(json).doesNotContain("username", "email", "g_", "object_key");
        assertThat(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(LobbyBootstrapService.MAX_RESPONSE_BYTES);
        verify(fixture.repository()).resolve("vi-VN");
        verify(fixture.repository()).findAssets(List.of());
    }

    @Test
    void incompleteProfileFailsBeforeContentResolution() {
        Fixture fixture = fixture(new ProfileIdentityResponse(
                UUID.randomUUID(), null, "REQUIRED", 1));

        assertThatThrownBy(() ->
                fixture.service().bootstrap(UUID.randomUUID(), "vi-VN"))
                .isInstanceOf(LobbyBootstrapException.class)
                .extracting("status", "code")
                .containsExactly(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "PROFILE_ONBOARDING_REQUIRED"
                );
        verify(fixture.repository(), never()).resolve(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requiredProfileTimeoutReturnsServiceUnavailableProblem() {
        PlayerProfileService profileService = mock(PlayerProfileService.class);
        when(profileService.identity(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new QueryTimeoutException("profile timeout"));
        LobbyBootstrapService service = service(
                profileService, mock(LobbyContentReadRepository.class));

        assertThatThrownBy(() ->
                service.bootstrap(UUID.randomUUID(), "vi-VN"))
                .isInstanceOf(LobbyBootstrapException.class)
                .extracting("status", "code")
                .containsExactly(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "PROFILE_UNAVAILABLE"
                );
    }

    @Test
    void recognizedContentTimeoutReturnsNoStoreDegradedShell() {
        Fixture fixture = fixture(new ProfileIdentityResponse(
                UUID.randomUUID(), "Titan", "COMPLETED", 2));
        when(fixture.repository().resolve("en-US"))
                .thenThrow(new QueryTimeoutException("bounded timeout"));

        LobbyBootstrapService.BootstrapResult result =
                fixture.service().bootstrap(UUID.randomUUID(), "en-US");

        assertThat(result.noStore()).isTrue();
        assertThat(result.response().degraded().codes())
                .containsExactly("CONTENT_UNAVAILABLE");
        assertThat(result.response().navigation()).isEmpty();
        assertThat(result.response().sections()).isEmpty();
    }

    @Test
    void integrityAndPermissionFailuresAreNeverSilentlyDegraded() {
        Fixture integrity = fixture(new ProfileIdentityResponse(
                UUID.randomUUID(), "Titan", "COMPLETED", 2));
        when(integrity.repository().resolve("vi-VN"))
                .thenThrow(new DataIntegrityViolationException("checksum mismatch"));
        Fixture permission = fixture(new ProfileIdentityResponse(
                UUID.randomUUID(), "Titan", "COMPLETED", 2));
        when(permission.repository().resolve("vi-VN"))
                .thenThrow(new PermissionDeniedDataAccessException(
                        "permission denied", new SQLException("denied")));

        assertThatThrownBy(() ->
                integrity.service().bootstrap(UUID.randomUUID(), "vi-VN"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() ->
                permission.service().bootstrap(UUID.randomUUID(), "vi-VN"))
                .isInstanceOf(PermissionDeniedDataAccessException.class);
    }

    @Test
    void controllerReturnsPrivateHeadersAndBodylessMatchingWeakEtag() {
        LobbyBootstrapService service = mock(LobbyBootstrapService.class);
        LobbyLocaleResolver resolver = new LobbyLocaleResolver();
        LobbyBootstrapController controller =
                new LobbyBootstrapController(service, resolver);
        UUID userId = UUID.randomUUID();
        LobbyBootstrapResponse body = new LobbyBootstrapResponse(
                1, NOW, "vi-VN",
                new LobbyBootstrapResponse.PlayerSummary("Titan", 2),
                List.of(), List.of(), null,
                new LobbyBootstrapResponse.DegradedState(false, List.of()));
        LobbyBootstrapService.BootstrapResult result =
                new LobbyBootstrapService.BootstrapResult(
                        body, "W/\"" + "a".repeat(64) + "\"", false);
        when(service.bootstrap(userId, "vi-VN")).thenReturn(result);
        AuthenticatedUser user =
                new AuthenticatedUser(userId, UUID.randomUUID(), "PLAYER");

        ResponseEntity<?> ok = controller.bootstrap(user, "fr-FR", null);
        ResponseEntity<?> notModified =
                controller.bootstrap(user, "fr-FR", result.etag());

        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat(ok.getHeaders().getETag()).isEqualTo(result.etag());
        assertThat(ok.getHeaders().getFirst(HttpHeaders.CONTENT_LANGUAGE))
                .isEqualTo("vi-VN");
        assertThat(ok.getHeaders().getCacheControl())
                .isEqualTo("private, no-cache, must-revalidate");
        assertThat(ok.getHeaders().getFirst(HttpHeaders.VARY))
                .isEqualTo("Authorization, Accept-Language, Origin");
        assertThat(notModified.getStatusCode().value()).isEqualTo(304);
        assertThat(notModified.getBody()).isNull();
        assertThat(notModified.getHeaders().getETag()).isEqualTo(result.etag());
    }

    @Test
    void noStoreResponseNeverUsesConditionalCacheValidation() {
        LobbyBootstrapService service = mock(LobbyBootstrapService.class);
        LobbyBootstrapController controller =
                new LobbyBootstrapController(service, new LobbyLocaleResolver());
        UUID userId = UUID.randomUUID();
        LobbyBootstrapResponse body = new LobbyBootstrapResponse(
                1, NOW, "vi-VN",
                new LobbyBootstrapResponse.PlayerSummary("Titan", 2),
                List.of(), List.of(), null,
                new LobbyBootstrapResponse.DegradedState(
                        true, List.of("CONTENT_UNAVAILABLE")));
        LobbyBootstrapService.BootstrapResult result =
                new LobbyBootstrapService.BootstrapResult(
                        body, "W/\"" + "b".repeat(64) + "\"", true);
        when(service.bootstrap(userId, "vi-VN")).thenReturn(result);

        ResponseEntity<?> response = controller.bootstrap(
                new AuthenticatedUser(userId, UUID.randomUUID(), "PLAYER"),
                "vi-VN",
                result.etag()
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).isEqualTo(body);
    }

    private Fixture fixture(ProfileIdentityResponse profile) {
        PlayerProfileService profileService = mock(PlayerProfileService.class);
        LobbyContentReadRepository repository = mock(LobbyContentReadRepository.class);
        when(profileService.identity(org.mockito.ArgumentMatchers.any())).thenReturn(profile);
        return new Fixture(
                service(profileService, repository),
                repository
        );
    }

    private LobbyBootstrapService service(
            PlayerProfileService profileService,
            LobbyContentReadRepository repository
    ) {
        return new LobbyBootstrapService(
                profileService,
                repository,
                new LobbyComponentRegistry(OBJECT_MAPPER),
                new LobbyEtagBuilder(OBJECT_MAPPER),
                OBJECT_MAPPER,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private record Fixture(
            LobbyBootstrapService service,
            LobbyContentReadRepository repository
    ) {
    }
}
