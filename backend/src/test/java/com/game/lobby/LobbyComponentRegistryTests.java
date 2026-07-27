package com.game.lobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.lobby.dto.LobbyBootstrapResponse;
import com.game.lobby.dto.LobbySectionResponse;
import com.game.lobby.model.ResolvedLobbyContent;
import com.game.lobby.registry.LobbyComponentRegistry;
import com.game.lobby.registry.LobbyContentIntegrityException;
import com.game.lobby.service.LobbyEtagBuilder;
import com.game.lobby.service.LobbyLocaleResolver;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class LobbyComponentRegistryTests {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().findAndRegisterModules();
    private static final String CHECKSUM = "a".repeat(64);

    private final LobbyComponentRegistry registry =
            new LobbyComponentRegistry(OBJECT_MAPPER);

    @Test
    void mapsEveryApprovedTupleToTypedBoundedContracts() throws Exception {
        List<ResolvedLobbyContent.ResolvedPublication> publications = List.of(
                publication("NAVIGATION", "lobby.navigation", """
                        {"items":[
                          {"key":"inventory","label":"Inventory","iconKey":"BACKPACK",
                           "target":"INVENTORY","order":20,"visible":true},
                          {"key":"hidden","label":"Hidden","iconKey":"HOME",
                           "target":"LOBBY","order":10,"visible":false}
                        ]}
                        """),
                publication("BANNER", "lobby.hero", """
                        {"key":"hero","title":"Titan raid","copy":"Ready together",
                         "tone":"CELEBRATION","presentationVariant":"WIDE",
                         "order":10,"visible":true}
                        """),
                publication("ANNOUNCEMENT", "lobby.announcements", """
                        {"key":"news","order":20,"visible":true,"items":[
                          {"key":"one","copy":"Welcome","tone":"INFO","order":1}
                        ]}
                        """),
                publication("PAGE_SECTION", "lobby.boss-rooms", """
                        {"key":"bosses","title":"Boss raids","order":30,"visible":true,
                         "bosses":[{"bossCode":"ember-titan","name":"Ember Titan",
                         "summary":"A presentation preview","difficultyLabel":"Normal"}]}
                        """),
                publication("PAGE_SECTION", "lobby.player-summary", """
                        {"key":"player","title":"Player","order":40,"visible":true}
                        """),
                publication("PAGE_SECTION", "lobby.character-preview", """
                        {"key":"character","title":"Character","order":50,"visible":true}
                        """),
                publication("PAGE_SECTION", "lobby.inventory-preview", """
                        {"key":"inventory","title":"Inventory","emptyCopy":"No items",
                         "maxItems":6,"order":60,"visible":true}
                        """),
                publication("EVENT", "lobby.event-spotlight", """
                        {"key":"event","title":"Event","copy":"Limited presentation",
                         "tone":"INFO","order":70,"visible":true}
                        """)
        );
        Map<UUID, List<ResolvedLobbyContent.ResolvedAsset>> assets =
                publications.stream().collect(java.util.stream.Collectors.toMap(
                        ResolvedLobbyContent.ResolvedPublication::contentVersionId,
                        publication -> List.of(imageAsset(publication))
                ));

        LobbyComponentRegistry.MappedLobbyContent mapped =
                registry.map(publications, assets);

        assertThat(mapped.navigation()).hasSize(1);
        assertThat(mapped.navigation().getFirst().target())
                .isEqualTo(LobbyBootstrapResponse.NavigationTarget.INVENTORY);
        assertThat(mapped.sections()).hasSize(7);
        assertThat(mapped.sections()).extracting(LobbySectionResponse::type)
                .containsExactly(
                        "HERO_BANNER",
                        "ANNOUNCEMENT_STRIP",
                        "BOSS_ROOM_LIST",
                        "PLAYER_SUMMARY",
                        "CHARACTER_PREVIEW",
                        "INVENTORY_PREVIEW",
                        "EVENT_SPOTLIGHT"
                );
        assertThat(mapped.degradedCodes()).isEmpty();
    }

    @Test
    void rejectsUnknownFieldsTargetsSchemasAndIncompatibleAssetsWithoutRawLeakage()
            throws Exception {
        var unknownField = publication("PAGE_SECTION", "lobby.player-summary", """
                {"key":"player","title":"Player","order":1,"visible":true,
                 "route":"/admin"}
                """);
        var unknownSchema = new ResolvedLobbyContent.ResolvedPublication(
                UUID.randomUUID(), UUID.randomUUID(), 1, CHECKSUM,
                "PAGE_SECTION", "lobby.player-summary", 99,
                OBJECT_MAPPER.readTree("{}"));
        var badTarget = publication("NAVIGATION", "lobby.navigation", """
                {"items":[{"key":"admin","label":"Admin","iconKey":"HOME",
                "target":"ADMIN","order":1,"visible":true}]}
                """);
        var badAsset = publication("BANNER", "lobby.hero", """
                {"key":"hero","title":"Hero","copy":"Copy","tone":"INFO",
                "presentationVariant":"WIDE","order":1,"visible":true}
                """);
        ResolvedLobbyContent.ResolvedAsset executable = new ResolvedLobbyContent.ResolvedAsset(
                badAsset.contentVersionId(), UUID.randomUUID(), "HERO", 0,
                "CARD", "text/html", CHECKSUM, 640, 360, null, "APPROVED");

        var mapped = registry.map(
                List.of(unknownField, unknownSchema, badTarget, badAsset),
                Map.of(badAsset.contentVersionId(), List.of(executable))
        );

        assertThat(mapped.navigation()).isEmpty();
        assertThat(mapped.sections()).isEmpty();
        assertThat(mapped.degradedCodes()).containsExactly(
                "INVALID_CONTENT", "UNKNOWN_CONTENT_SCHEMA");
    }

    @Test
    void resolvesLocaleThroughAllowlistAndBuildsSemanticWeakEtags() {
        LobbyLocaleResolver resolver = new LobbyLocaleResolver();
        assertThat(resolver.resolve("en-US,en;q=0.8")).isEqualTo("en-US");
        assertThat(resolver.resolve("EN")).isEqualTo("en-US");
        assertThat(resolver.resolve("fr-FR,*;q=0.1")).isEqualTo("vi-VN");
        assertThat(resolver.resolve(null)).isEqualTo("vi-VN");

        LobbyEtagBuilder builder = new LobbyEtagBuilder(OBJECT_MAPPER);
        LobbyBootstrapResponse first = response(Instant.parse("2026-07-27T00:00:00Z"), 2);
        LobbyBootstrapResponse later = response(Instant.parse("2026-07-27T01:00:00Z"), 2);
        LobbyBootstrapResponse changedProfile =
                response(Instant.parse("2026-07-27T01:00:00Z"), 3);
        LobbyBootstrapResponse changedIdentity =
                response("Other Titan", Instant.parse("2026-07-27T01:00:00Z"), 2);
        LobbyBootstrapResponse changedContent = responseWithHeroChecksum(
                Instant.parse("2026-07-27T01:00:00Z"), "b".repeat(64),
                "c".repeat(64));
        LobbyBootstrapResponse changedAsset = responseWithHeroChecksum(
                Instant.parse("2026-07-27T01:00:00Z"), "b".repeat(64),
                "d".repeat(64));

        assertThat(builder.build(first)).startsWith("W/\"").endsWith("\"");
        assertThat(builder.build(first)).isEqualTo(builder.build(later));
        assertThat(builder.build(first)).isNotEqualTo(builder.build(changedProfile));
        assertThat(builder.build(first)).isNotEqualTo(builder.build(changedIdentity));
        assertThat(builder.build(changedContent))
                .isNotEqualTo(builder.build(changedAsset));
    }

    @Test
    void rejectsAssetSetsBeyondGlobalRepositoryAndResponseBudget() throws Exception {
        var hero = publication("BANNER", "lobby.hero", """
                {"key":"hero","title":"Hero","copy":"Copy","tone":"INFO",
                 "presentationVariant":"WIDE","order":1,"visible":true}
                """);
        List<ResolvedLobbyContent.ResolvedAsset> assets =
                java.util.stream.IntStream.range(0, 25)
                        .mapToObj(index -> new ResolvedLobbyContent.ResolvedAsset(
                                hero.contentVersionId(),
                                UUID.randomUUID(),
                                "HERO",
                                index,
                                "HERO",
                                "image/webp",
                                CHECKSUM,
                                640,
                                360,
                                null,
                                "APPROVED"
                        ))
                        .toList();

        var mapped = registry.map(
                List.of(hero),
                Map.of(hero.contentVersionId(), assets)
        );

        assertThat(mapped.sections()).isEmpty();
        assertThat(mapped.degradedCodes())
                .containsExactly("INVALID_CONTENT", "MISSING_ASSET");
    }

    @Test
    void rejectsNavigationOverflowInsteadOfSilentlyTruncating() throws Exception {
        var first = publication("NAVIGATION", "lobby.navigation", """
                {"items":[
                  {"key":"n1","label":"One","iconKey":"HOME","target":"LOBBY","order":1,"visible":true},
                  {"key":"n2","label":"Two","iconKey":"HOME","target":"LOBBY","order":2,"visible":true},
                  {"key":"n3","label":"Three","iconKey":"HOME","target":"LOBBY","order":3,"visible":true},
                  {"key":"n4","label":"Four","iconKey":"HOME","target":"LOBBY","order":4,"visible":true}
                ]}
                """);
        var second = publication("NAVIGATION", "lobby.navigation", """
                {"items":[
                  {"key":"n5","label":"Five","iconKey":"HOME","target":"LOBBY","order":5,"visible":true},
                  {"key":"n6","label":"Six","iconKey":"HOME","target":"LOBBY","order":6,"visible":true},
                  {"key":"n7","label":"Seven","iconKey":"HOME","target":"LOBBY","order":7,"visible":true},
                  {"key":"n8","label":"Eight","iconKey":"HOME","target":"LOBBY","order":8,"visible":true}
                ]}
                """);

        assertThatThrownBy(() -> registry.map(List.of(first, second), Map.of()))
                .isInstanceOf(LobbyContentIntegrityException.class);
    }

    @Test
    void rejectsSectionOverflowInsteadOfSilentlyTruncating() throws Exception {
        List<ResolvedLobbyContent.ResolvedPublication> duplicates =
                new java.util.ArrayList<>();
        for (int index = 0; index < 8; index++) {
            duplicates.add(publication(
                    "PAGE_SECTION",
                    "lobby.player-summary",
                    ("{\"key\":\"player-%d\",\"title\":\"Player\","
                            + "\"order\":%d,\"visible\":true}")
                            .formatted(index, index)
            ));
        }

        assertThatThrownBy(() -> registry.map(duplicates, Map.of()))
                .isInstanceOf(LobbyContentIntegrityException.class);
    }

    private ResolvedLobbyContent.ResolvedPublication publication(
            String type,
            String slot,
            String payload
    ) throws Exception {
        return new ResolvedLobbyContent.ResolvedPublication(
                UUID.randomUUID(), UUID.randomUUID(), 1, CHECKSUM,
                type, slot, 1, OBJECT_MAPPER.readTree(payload));
    }

    private ResolvedLobbyContent.ResolvedAsset imageAsset(
            ResolvedLobbyContent.ResolvedPublication publication
    ) {
        String variant = switch (publication.slotKey()) {
            case "lobby.boss-rooms", "lobby.character-preview" -> "CARD";
            default -> "HERO";
        };
        return new ResolvedLobbyContent.ResolvedAsset(
                publication.contentVersionId(), UUID.randomUUID(), "PRIMARY", 0, variant,
                "image/webp", CHECKSUM, 640, 360, null, "APPROVED");
    }

    private LobbyBootstrapResponse responseWithHeroChecksum(
            Instant generatedAt,
            String contentChecksum,
            String assetChecksum
    ) {
        var contentRef = new LobbySectionResponse.ContentRef(
                "00000000-0000-0000-0000-000000000001",
                "00000000-0000-0000-0000-000000000002",
                1,
                contentChecksum
        );
        var asset = new LobbySectionResponse.AssetDescriptor(
                "00000000-0000-0000-0000-000000000003",
                "HERO",
                "image/webp",
                640,
                360,
                null,
                assetChecksum,
                null
        );
        var hero = new LobbySectionResponse.HeroBanner(
                "hero",
                "HERO_BANNER",
                1,
                "Titan",
                "Raid",
                "INFO",
                "WIDE",
                contentRef,
                List.of(asset)
        );
        return new LobbyBootstrapResponse(
                1, generatedAt, "vi-VN",
                new LobbyBootstrapResponse.PlayerSummary("Titan", 2),
                List.of(), List.of(hero), null,
                new LobbyBootstrapResponse.DegradedState(false, List.of())
        );
    }

    private LobbyBootstrapResponse response(Instant generatedAt, long profileVersion) {
        return response("Titan", generatedAt, profileVersion);
    }

    private LobbyBootstrapResponse response(
            String displayName,
            Instant generatedAt,
            long profileVersion
    ) {
        return new LobbyBootstrapResponse(
                1, generatedAt, "vi-VN",
                new LobbyBootstrapResponse.PlayerSummary(displayName, profileVersion),
                List.of(), List.of(), null,
                new LobbyBootstrapResponse.DegradedState(false, List.of())
        );
    }
}
