package com.game.lobby.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.lobby.dto.LobbyBootstrapResponse;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed ObjectMapper is intentionally injected")
public class LobbyEtagBuilder {

    private final ObjectMapper objectMapper;

    public LobbyEtagBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(LobbyBootstrapResponse response) {
        SemanticBootstrap semantic = new SemanticBootstrap(
                response.schemaVersion(),
                response.locale(),
                response.player().displayName(),
                response.player().profileVersion(),
                response.navigation(),
                response.sections(),
                response.nextContentBoundaryAt(),
                response.degraded().codes()
        );
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(semantic);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(serialized);
            return "W/\"" + HexFormat.of().formatHex(digest) + "\"";
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not build lobby response validator",
                    exception);
        }
    }

    private record SemanticBootstrap(
            int schemaVersion,
            String locale,
            String displayName,
            long profileVersion,
            java.util.List<LobbyBootstrapResponse.NavigationItem> navigation,
            java.util.List<com.game.lobby.dto.LobbySectionResponse> sections,
            java.time.Instant nextContentBoundaryAt,
            java.util.List<String> degradedCodes
    ) {
    }
}
