package com.game.lobby.dto;

import java.util.List;

public sealed interface LobbySectionResponse permits
        LobbySectionResponse.HeroBanner,
        LobbySectionResponse.AnnouncementStrip,
        LobbySectionResponse.BossRoomList,
        LobbySectionResponse.PlayerSummarySection,
        LobbySectionResponse.CharacterPreview,
        LobbySectionResponse.InventoryPreview,
        LobbySectionResponse.EventSpotlight {

    String key();

    String type();

    int order();

    ContentRef contentRef();

    List<AssetDescriptor> assets();

    record ContentRef(
            String publicationId,
            String contentVersionId,
            long version,
            String checksum
    ) {
    }

    record AssetDescriptor(
            String assetId,
            String variantKey,
            String mediaType,
            Integer width,
            Integer height,
            Long durationMillis,
            String checksum,
            String deliveryUrl
    ) {
    }

    record HeroBanner(
            String key,
            String type,
            int order,
            String title,
            String copy,
            String tone,
            String presentationVariant,
            ContentRef contentRef,
            List<AssetDescriptor> assets
    ) implements LobbySectionResponse {
        public HeroBanner {
            assets = List.copyOf(assets);
        }
    }

    record AnnouncementStrip(
            String key,
            String type,
            int order,
            List<Announcement> announcements,
            ContentRef contentRef,
            List<AssetDescriptor> assets
    ) implements LobbySectionResponse {
        public AnnouncementStrip {
            announcements = List.copyOf(announcements);
            assets = List.copyOf(assets);
        }
    }

    record Announcement(String key, String copy, String tone, int order) {
    }

    record BossRoomList(
            String key,
            String type,
            int order,
            String title,
            List<BossPreview> bosses,
            ContentRef contentRef,
            List<AssetDescriptor> assets
    ) implements LobbySectionResponse {
        public BossRoomList {
            bosses = List.copyOf(bosses);
            assets = List.copyOf(assets);
        }
    }

    record BossPreview(
            String bossCode,
            String name,
            String summary,
            String difficultyLabel
    ) {
    }

    record PlayerSummarySection(
            String key,
            String type,
            int order,
            String label,
            ContentRef contentRef,
            List<AssetDescriptor> assets
    ) implements LobbySectionResponse {
        public PlayerSummarySection {
            assets = List.copyOf(assets);
        }
    }

    record CharacterPreview(
            String key,
            String type,
            int order,
            String title,
            ContentRef contentRef,
            List<AssetDescriptor> assets
    ) implements LobbySectionResponse {
        public CharacterPreview {
            assets = List.copyOf(assets);
        }
    }

    record InventoryPreview(
            String key,
            String type,
            int order,
            String title,
            String emptyCopy,
            int maxItems,
            ContentRef contentRef,
            List<AssetDescriptor> assets
    ) implements LobbySectionResponse {
        public InventoryPreview {
            assets = List.copyOf(assets);
        }
    }

    record EventSpotlight(
            String key,
            String type,
            int order,
            String title,
            String copy,
            String tone,
            ContentRef contentRef,
            List<AssetDescriptor> assets
    ) implements LobbySectionResponse {
        public EventSpotlight {
            assets = List.copyOf(assets);
        }
    }
}
