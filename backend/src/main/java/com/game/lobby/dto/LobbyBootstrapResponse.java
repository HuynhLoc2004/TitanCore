package com.game.lobby.dto;

import java.time.Instant;
import java.util.List;

public record LobbyBootstrapResponse(
        int schemaVersion,
        Instant generatedAt,
        String locale,
        PlayerSummary player,
        List<NavigationItem> navigation,
        List<LobbySectionResponse> sections,
        Instant nextContentBoundaryAt,
        DegradedState degraded
) {
    public LobbyBootstrapResponse {
        navigation = List.copyOf(navigation);
        sections = List.copyOf(sections);
    }

    public record PlayerSummary(String displayName, long profileVersion) {
    }

    public record NavigationItem(
            String key,
            String label,
            String iconKey,
            NavigationTarget target,
            int order
    ) {
    }

    public enum NavigationTarget {
        LOBBY,
        INVENTORY,
        SETTINGS
    }

    public record DegradedState(boolean active, List<String> codes) {
        public DegradedState {
            codes = List.copyOf(codes);
        }
    }
}
