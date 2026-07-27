package com.game.lobby.registry;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.lobby.dto.LobbyBootstrapResponse.NavigationItem;
import com.game.lobby.dto.LobbyBootstrapResponse.NavigationTarget;
import com.game.lobby.dto.LobbySectionResponse;
import com.game.lobby.dto.LobbySectionResponse.AssetDescriptor;
import com.game.lobby.dto.LobbySectionResponse.ContentRef;
import com.game.lobby.model.LobbyContentTuple;
import com.game.lobby.model.ResolvedLobbyContent.ResolvedAsset;
import com.game.lobby.model.ResolvedLobbyContent.ResolvedPublication;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed ObjectMapper is intentionally injected")
public class LobbyComponentRegistry {

    public static final int MAX_NAVIGATION = 7;
    public static final int MAX_SECTIONS = 7;
    public static final int MAX_ASSETS = 24;

    private static final Pattern SAFE_KEY =
            Pattern.compile("^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$");
    private static final Set<String> SAFE_IMAGE_MEDIA_TYPES =
            Set.of("image/avif", "image/jpeg", "image/png", "image/webp");
    private static final Set<String> HERO_VARIANTS =
            Set.of("HERO", "MOBILE", "DESKTOP");
    private static final Set<String> CARD_VARIANTS =
            Set.of("CARD", "THUMBNAIL", "PORTRAIT");
    private static final Set<String> HERO_ROLES = Set.of("HERO", "PRIMARY");
    private static final Set<String> CARD_ROLES =
            Set.of("PRIMARY", "THUMBNAIL", "HERO", "PORTRAIT");

    private final ObjectMapper objectMapper;

    public LobbyComponentRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MappedLobbyContent map(
            List<ResolvedPublication> publications,
            Map<java.util.UUID, List<ResolvedAsset>> assetsByVersion
    ) {
        List<NavigationItem> navigation = new ArrayList<>();
        List<LobbySectionResponse> sections = new ArrayList<>();
        EnumSet<DegradedCode> degraded = EnumSet.noneOf(DegradedCode.class);
        int resolvedAssetCount = assetsByVersion.values().stream()
                .mapToInt(List::size)
                .sum();
        Map<java.util.UUID, List<ResolvedAsset>> boundedAssets = assetsByVersion;
        if (resolvedAssetCount > MAX_ASSETS) {
            degraded.add(DegradedCode.INVALID_CONTENT);
            boundedAssets = Map.of();
        }
        for (ResolvedPublication publication : publications) {
            LobbyContentTuple tuple = LobbyContentTuple.find(
                    publication.contentType(),
                    publication.slotKey(),
                    publication.schemaVersion()
            ).orElse(null);
            if (tuple == null) {
                degraded.add(DegradedCode.UNKNOWN_CONTENT_SCHEMA);
                continue;
            }
            try {
                List<ResolvedAsset> assets = boundedAssets.getOrDefault(
                        publication.contentVersionId(), List.of());
                require(assets.stream().allMatch(asset ->
                        publication.contentVersionId().equals(asset.contentVersionId())));
                if (tuple == LobbyContentTuple.NAVIGATION) {
                    navigation.addAll(mapNavigation(publication.payload()));
                } else {
                    LobbySectionResponse section = mapSection(tuple, publication, assets);
                    if (section != null) {
                        sections.add(section);
                    }
                }
            } catch (InvalidLobbyContentException exception) {
                degraded.add(exception.code());
            }
        }
        navigation.sort(Comparator.comparingInt(NavigationItem::order)
                .thenComparing(NavigationItem::key));
        sections.sort(Comparator.comparingInt(LobbySectionResponse::order)
                .thenComparing(LobbySectionResponse::key));
        int assetCount = sections.stream()
                .mapToInt(section -> section.assets().size())
                .sum();
        require(assetCount <= MAX_ASSETS);
        assertInvariant(navigation.size() <= MAX_NAVIGATION);
        assertInvariant(sections.size() <= MAX_SECTIONS);
        return new MappedLobbyContent(
                List.copyOf(navigation),
                List.copyOf(sections),
                degraded.stream().map(Enum::name).sorted().toList()
        );
    }

    private List<NavigationItem> mapNavigation(JsonNode payload) {
        NavigationPayload value = read(payload, NavigationPayload.class);
        require(value.items() != null && value.items().size() <= MAX_NAVIGATION);
        List<NavigationItem> result = new ArrayList<>();
        for (NavigationPayload.Item item : value.items()) {
            if (!item.visible()) {
                continue;
            }
            requireKey(item.key());
            requireText(item.label(), 64);
            require(item.iconKey() != null);
            require(item.target() != null);
            requireOrder(item.order());
            result.add(new NavigationItem(
                    item.key(), item.label().strip(), item.iconKey().name(),
                    item.target(), item.order()));
        }
        return result;
    }

    private LobbySectionResponse mapSection(
            LobbyContentTuple tuple,
            ResolvedPublication publication,
            List<ResolvedAsset> resolvedAssets
    ) {
        return switch (tuple) {
            case HERO_BANNER -> mapHero(publication, resolvedAssets);
            case ANNOUNCEMENT_STRIP -> mapAnnouncements(publication);
            case BOSS_ROOM_LIST -> mapBosses(publication, resolvedAssets);
            case PLAYER_SUMMARY -> mapPlayerSummary(publication);
            case CHARACTER_PREVIEW -> mapCharacter(publication, resolvedAssets);
            case INVENTORY_PREVIEW -> mapInventory(publication);
            case EVENT_SPOTLIGHT -> mapEvent(publication, resolvedAssets);
            case NAVIGATION -> throw invalid();
        };
    }

    private LobbySectionResponse mapHero(
            ResolvedPublication publication,
            List<ResolvedAsset> assets
    ) {
        HeroPayload value = read(publication.payload(), HeroPayload.class);
        if (!value.visible()) {
            return null;
        }
        validateCommon(value.key(), value.title(), value.order());
        requireText(value.copy(), 500);
        require(value.tone() != null && value.presentationVariant() != null);
        List<AssetDescriptor> descriptors =
                mapAssets(assets, HERO_ROLES, HERO_VARIANTS, 2, true);
        return new LobbySectionResponse.HeroBanner(
                value.key(), "HERO_BANNER", value.order(), value.title().strip(),
                value.copy().strip(), value.tone().name(),
                value.presentationVariant().name(), contentRef(publication), descriptors);
    }

    private LobbySectionResponse mapAnnouncements(ResolvedPublication publication) {
        AnnouncementPayload value = read(
                publication.payload(), AnnouncementPayload.class);
        if (!value.visible()) {
            return null;
        }
        requireKey(value.key());
        requireOrder(value.order());
        require(value.items() != null && value.items().size() <= 3);
        List<LobbySectionResponse.Announcement> items = new ArrayList<>();
        for (AnnouncementPayload.Item item : value.items()) {
            requireKey(item.key());
            requireText(item.copy(), 240);
            require(item.tone() != null);
            requireOrder(item.order());
            items.add(new LobbySectionResponse.Announcement(
                    item.key(), item.copy().strip(), item.tone().name(), item.order()));
        }
        items.sort(Comparator.comparingInt(LobbySectionResponse.Announcement::order)
                .thenComparing(LobbySectionResponse.Announcement::key));
        return new LobbySectionResponse.AnnouncementStrip(
                value.key(), "ANNOUNCEMENT_STRIP", value.order(), List.copyOf(items),
                contentRef(publication), List.of());
    }

    private LobbySectionResponse mapBosses(
            ResolvedPublication publication,
            List<ResolvedAsset> assets
    ) {
        BossListPayload value = read(publication.payload(), BossListPayload.class);
        if (!value.visible()) {
            return null;
        }
        validateCommon(value.key(), value.title(), value.order());
        require(value.bosses() != null && value.bosses().size() <= 6);
        List<LobbySectionResponse.BossPreview> bosses = value.bosses().stream()
                .map(boss -> {
                    requireKey(boss.bossCode());
                    requireText(boss.name(), 64);
                    requireText(boss.summary(), 240);
                    requireText(boss.difficultyLabel(), 32);
                    return new LobbySectionResponse.BossPreview(
                            boss.bossCode(), boss.name().strip(), boss.summary().strip(),
                            boss.difficultyLabel().strip());
                })
                .toList();
        return new LobbySectionResponse.BossRoomList(
                value.key(), "BOSS_ROOM_LIST", value.order(), value.title().strip(),
                bosses, contentRef(publication),
                mapAssets(
                        assets, CARD_ROLES, CARD_VARIANTS, 6, !bosses.isEmpty()));
    }

    private LobbySectionResponse mapPlayerSummary(ResolvedPublication publication) {
        SimplePayload value = read(publication.payload(), SimplePayload.class);
        if (!value.visible()) {
            return null;
        }
        validateCommon(value.key(), value.title(), value.order());
        return new LobbySectionResponse.PlayerSummarySection(
                value.key(), "PLAYER_SUMMARY", value.order(), value.title().strip(),
                contentRef(publication), List.of());
    }

    private LobbySectionResponse mapCharacter(
            ResolvedPublication publication,
            List<ResolvedAsset> assets
    ) {
        SimplePayload value = read(publication.payload(), SimplePayload.class);
        if (!value.visible()) {
            return null;
        }
        validateCommon(value.key(), value.title(), value.order());
        return new LobbySectionResponse.CharacterPreview(
                value.key(), "CHARACTER_PREVIEW", value.order(), value.title().strip(),
                contentRef(publication),
                mapAssets(assets, CARD_ROLES, CARD_VARIANTS, 1, false));
    }

    private LobbySectionResponse mapInventory(ResolvedPublication publication) {
        InventoryPayload value = read(publication.payload(), InventoryPayload.class);
        if (!value.visible()) {
            return null;
        }
        validateCommon(value.key(), value.title(), value.order());
        requireText(value.emptyCopy(), 160);
        require(value.maxItems() >= 0 && value.maxItems() <= 6);
        return new LobbySectionResponse.InventoryPreview(
                value.key(), "INVENTORY_PREVIEW", value.order(), value.title().strip(),
                value.emptyCopy().strip(), value.maxItems(), contentRef(publication),
                List.of());
    }

    private LobbySectionResponse mapEvent(
            ResolvedPublication publication,
            List<ResolvedAsset> assets
    ) {
        EventPayload value = read(publication.payload(), EventPayload.class);
        if (!value.visible()) {
            return null;
        }
        validateCommon(value.key(), value.title(), value.order());
        requireText(value.copy(), 500);
        require(value.tone() != null);
        return new LobbySectionResponse.EventSpotlight(
                value.key(), "EVENT_SPOTLIGHT", value.order(), value.title().strip(),
                value.copy().strip(), value.tone().name(), contentRef(publication),
                mapAssets(assets, HERO_ROLES, HERO_VARIANTS, 1, true));
    }

    private List<AssetDescriptor> mapAssets(
            List<ResolvedAsset> assets,
            Set<String> roles,
            Set<String> variants,
            int maximum,
            boolean required
    ) {
        List<AssetDescriptor> descriptors = assets.stream()
                .filter(asset -> roles.contains(asset.roleKey()))
                .limit(maximum + 1L)
                .map(asset -> mapAsset(asset, variants))
                .toList();
        require(descriptors.size() <= maximum);
        if (required && descriptors.isEmpty()) {
            throw new InvalidLobbyContentException(DegradedCode.MISSING_ASSET);
        }
        return descriptors;
    }

    private AssetDescriptor mapAsset(
            ResolvedAsset asset,
            Set<String> allowedVariants
    ) {
        require(asset.variantKey() != null
                && allowedVariants.contains(asset.variantKey()));
        require(asset.mediaType() != null
                && SAFE_IMAGE_MEDIA_TYPES.contains(asset.mediaType()));
        require(asset.width() != null && asset.width() > 0);
        require(asset.height() != null && asset.height() > 0);
        require(asset.checksum() != null && asset.checksum().matches("[0-9a-f]{64}"));
        require("APPROVED".equals(asset.reviewState())
                || "ARCHIVED".equals(asset.reviewState()));
        return new AssetDescriptor(
                asset.assetId().toString(), asset.variantKey(), asset.mediaType(),
                asset.width(), asset.height(), null, asset.checksum(), null);
    }

    private ContentRef contentRef(ResolvedPublication publication) {
        return new ContentRef(
                publication.publicationId().toString(),
                publication.contentVersionId().toString(),
                publication.versionNumber(),
                publication.checksum()
        );
    }

    private <T> T read(JsonNode payload, Class<T> type) {
        try {
            return objectMapper.readerFor(type)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(payload);
        } catch (IOException | RuntimeException exception) {
            throw invalid();
        }
    }

    private void validateCommon(String key, String title, int order) {
        requireKey(key);
        requireText(title, 96);
        requireOrder(order);
    }

    private void requireKey(String value) {
        require(value != null && value.length() <= 64 && SAFE_KEY.matcher(value).matches());
    }

    private void requireText(String value, int maximum) {
        require(value != null && !value.isBlank() && value.length() <= maximum);
    }

    private void requireOrder(int order) {
        require(order >= 0 && order <= 1000);
    }

    private void require(boolean condition) {
        if (!condition) {
            throw invalid();
        }
    }

    private void assertInvariant(boolean condition) {
        if (!condition) {
            throw new LobbyContentIntegrityException();
        }
    }

    private InvalidLobbyContentException invalid() {
        return new InvalidLobbyContentException(DegradedCode.INVALID_CONTENT);
    }

    public record MappedLobbyContent(
            List<NavigationItem> navigation,
            List<LobbySectionResponse> sections,
            List<String> degradedCodes
    ) {
        public MappedLobbyContent {
            navigation = List.copyOf(navigation);
            sections = List.copyOf(sections);
            degradedCodes = List.copyOf(degradedCodes);
        }

        public MappedLobbyContent withDegradedCode(String code) {
            EnumSet<DegradedCode> merged = EnumSet.noneOf(DegradedCode.class);
            degradedCodes.stream()
                    .map(DegradedCode::valueOf)
                    .forEach(merged::add);
            merged.add(DegradedCode.valueOf(code));
            return new MappedLobbyContent(
                    navigation,
                    sections,
                    merged.stream().map(Enum::name).sorted().toList()
            );
        }
    }

    public enum DegradedCode {
        UNKNOWN_CONTENT_SCHEMA,
        INVALID_CONTENT,
        MISSING_ASSET,
        CONTENT_UNAVAILABLE
    }

    private enum IconKey {
        HOME,
        BACKPACK,
        SETTINGS
    }

    private enum Tone {
        NEUTRAL,
        INFO,
        CELEBRATION,
        WARNING
    }

    private enum PresentationVariant {
        WIDE,
        COMPACT
    }

    private record NavigationPayload(List<Item> items) {
        private record Item(
                String key,
                String label,
                IconKey iconKey,
                NavigationTarget target,
                int order,
                boolean visible
        ) {
        }
    }

    private record HeroPayload(
            String key,
            String title,
            String copy,
            Tone tone,
            PresentationVariant presentationVariant,
            int order,
            boolean visible
    ) {
    }

    private record AnnouncementPayload(
            String key,
            int order,
            boolean visible,
            List<Item> items
    ) {
        private record Item(String key, String copy, Tone tone, int order) {
        }
    }

    private record BossListPayload(
            String key,
            String title,
            int order,
            boolean visible,
            List<Boss> bosses
    ) {
        private record Boss(
                String bossCode,
                String name,
                String summary,
                String difficultyLabel
        ) {
        }
    }

    private record SimplePayload(
            String key,
            String title,
            int order,
            boolean visible
    ) {
    }

    private record InventoryPayload(
            String key,
            String title,
            String emptyCopy,
            int maxItems,
            int order,
            boolean visible
    ) {
    }

    private record EventPayload(
            String key,
            String title,
            String copy,
            Tone tone,
            int order,
            boolean visible
    ) {
    }

    private static final class InvalidLobbyContentException extends RuntimeException {

        private final DegradedCode code;

        private InvalidLobbyContentException(DegradedCode code) {
            super("Invalid optional lobby content");
            this.code = code;
        }

        private DegradedCode code() {
            return code;
        }
    }
}
