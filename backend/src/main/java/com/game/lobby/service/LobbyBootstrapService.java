package com.game.lobby.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.auth.dto.ProfileIdentityResponse;
import com.game.auth.service.AuthException;
import com.game.lobby.dto.LobbyBootstrapResponse;
import com.game.lobby.model.ResolvedLobbyContent;
import com.game.lobby.registry.LobbyContentIntegrityException;
import com.game.lobby.registry.LobbyComponentRegistry;
import com.game.player.service.PlayerProfileService;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed collaborators are intentionally injected")
public class LobbyBootstrapService {

    public static final int RESPONSE_SCHEMA_VERSION = 1;
    public static final int MAX_RESPONSE_BYTES = 96 * 1024;

    private final PlayerProfileService playerProfileService;
    private final LobbyContentQueryService contentQueryService;
    private final LobbyEtagBuilder etagBuilder;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LobbyBootstrapService(
            PlayerProfileService playerProfileService,
            LobbyContentQueryService contentQueryService,
            LobbyEtagBuilder etagBuilder,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.playerProfileService = playerProfileService;
        this.contentQueryService = contentQueryService;
        this.etagBuilder = etagBuilder;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public BootstrapResult bootstrap(UUID userId, String locale) {
        ProfileIdentityResponse profile = publicProfile(userId);
        if (!"COMPLETED".equals(profile.onboardingStatus())
                || profile.displayName() == null) {
            throw new LobbyBootstrapException(
                    HttpStatus.CONFLICT,
                    "PROFILE_ONBOARDING_REQUIRED",
                    "Profile onboarding is required"
            );
        }

        ResolvedLobbyContent resolved;
        LobbyComponentRegistry.MappedLobbyContent mapped;
        boolean noStore = false;
        try {
            LobbyContentQueryService.ContentResult content =
                    contentQueryService.read(locale);
            resolved = content.resolved();
            mapped = content.mapped();
        } catch (LobbyContentIntegrityException exception) {
            throw new LobbyBootstrapException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "LOBBY_CONTENT_INTEGRITY_FAILURE",
                    "Lobby content is temporarily unavailable"
            );
        } catch (RuntimeException exception) {
            if (!hasSqlState(exception, "57014")) {
                throw exception;
            }
            resolved = new ResolvedLobbyContent(
                    clock.instant(), null, false, List.of());
            mapped = new LobbyComponentRegistry.MappedLobbyContent(
                    List.of(), List.of(), List.of("CONTENT_UNAVAILABLE"));
            noStore = true;
        }

        LobbyBootstrapResponse response = new LobbyBootstrapResponse(
                RESPONSE_SCHEMA_VERSION,
                clock.instant(),
                locale,
                new LobbyBootstrapResponse.PlayerSummary(
                        profile.displayName(), profile.version()),
                mapped.navigation(),
                mapped.sections(),
                resolved.nextBoundaryAt(),
                new LobbyBootstrapResponse.DegradedState(
                        !mapped.degradedCodes().isEmpty(),
                        mapped.degradedCodes()
                )
        );
        enforceSerializedBound(response);
        return new BootstrapResult(response, etagBuilder.build(response), noStore);
    }

    private boolean hasSqlState(Throwable throwable, String expectedSqlState) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && expectedSqlState.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ProfileIdentityResponse publicProfile(UUID userId) {
        try {
            return playerProfileService.identity(userId);
        } catch (QueryTimeoutException
                 | DataAccessResourceFailureException exception) {
            throw unavailableProfile();
        } catch (AuthException exception) {
            if ("PROFILE_NOT_FOUND".equals(exception.code())) {
                throw unavailableProfile();
            }
            throw exception;
        }
    }

    private void enforceSerializedBound(LobbyBootstrapResponse response) {
        try {
            if (objectMapper.writeValueAsBytes(response).length > MAX_RESPONSE_BYTES) {
                throw new IllegalStateException("Lobby bootstrap response exceeded bound");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Lobby bootstrap response could not serialize",
                    exception);
        }
    }

    private LobbyBootstrapException unavailableProfile() {
        return new LobbyBootstrapException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "PROFILE_UNAVAILABLE",
                "Player profile is temporarily unavailable"
        );
    }

    public record BootstrapResult(
            LobbyBootstrapResponse response,
            String etag,
            boolean noStore
    ) {
    }
}
