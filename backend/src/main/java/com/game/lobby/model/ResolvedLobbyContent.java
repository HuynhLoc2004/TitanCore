package com.game.lobby.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResolvedLobbyContent(
        Instant databaseTime,
        Instant nextBoundaryAt,
        boolean unknownContentPresent,
        List<ResolvedPublication> publications
) {
    public ResolvedLobbyContent {
        publications = List.copyOf(publications);
    }

    public record ResolvedPublication(
            UUID publicationId,
            UUID contentVersionId,
            long versionNumber,
            String checksum,
            String contentType,
            String slotKey,
            int schemaVersion,
            JsonNode payload
    ) {
    }

    public record ResolvedAsset(
            UUID contentVersionId,
            UUID assetId,
            String roleKey,
            int sortOrder,
            String variantKey,
            String mediaType,
            String checksum,
            Integer width,
            Integer height,
            Long durationMillis,
            String reviewState
    ) {
    }
}
