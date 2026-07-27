package com.game.lobby.service;

import com.game.lobby.model.ResolvedLobbyContent;
import com.game.lobby.registry.LobbyComponentRegistry;
import com.game.lobby.repository.LobbyContentReadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LobbyContentQueryService {

    private final LobbyContentReadRepository contentRepository;
    private final LobbyComponentRegistry componentRegistry;

    public LobbyContentQueryService(
            LobbyContentReadRepository contentRepository,
            LobbyComponentRegistry componentRegistry
    ) {
        this.contentRepository = contentRepository;
        this.componentRegistry = componentRegistry;
    }

    @Transactional(readOnly = true, timeout = 1)
    public ContentResult read(String locale) {
        contentRepository.applyTransactionLocalTimeout();
        ResolvedLobbyContent resolved = contentRepository.resolve(locale);
        List<UUID> versionIds = resolved.publications().stream()
                .map(ResolvedLobbyContent.ResolvedPublication::contentVersionId)
                .toList();
        Map<UUID, List<ResolvedLobbyContent.ResolvedAsset>> assetsByVersion =
                contentRepository.findAssets(versionIds).stream()
                        .collect(Collectors.groupingBy(
                                ResolvedLobbyContent.ResolvedAsset::contentVersionId
                        ));
        LobbyComponentRegistry.MappedLobbyContent mapped =
                componentRegistry.map(resolved.publications(), assetsByVersion);
        if (resolved.unknownContentPresent()) {
            mapped = mapped.withDegradedCode("UNKNOWN_CONTENT_SCHEMA");
        }
        return new ContentResult(resolved, mapped);
    }

    public record ContentResult(
            ResolvedLobbyContent resolved,
            LobbyComponentRegistry.MappedLobbyContent mapped
    ) {
    }
}
