package com.game.lobby.model;

import java.util.Arrays;
import java.util.Optional;

public enum LobbyContentTuple {
    NAVIGATION("NAVIGATION", "lobby.navigation", 1, null),
    HERO_BANNER("BANNER", "lobby.hero", 1, "HERO_BANNER"),
    ANNOUNCEMENT_STRIP(
            "ANNOUNCEMENT", "lobby.announcements", 1, "ANNOUNCEMENT_STRIP"),
    BOSS_ROOM_LIST("PAGE_SECTION", "lobby.boss-rooms", 1, "BOSS_ROOM_LIST"),
    PLAYER_SUMMARY("PAGE_SECTION", "lobby.player-summary", 1, "PLAYER_SUMMARY"),
    CHARACTER_PREVIEW(
            "PAGE_SECTION", "lobby.character-preview", 1, "CHARACTER_PREVIEW"),
    INVENTORY_PREVIEW(
            "PAGE_SECTION", "lobby.inventory-preview", 1, "INVENTORY_PREVIEW"),
    EVENT_SPOTLIGHT("EVENT", "lobby.event-spotlight", 1, "EVENT_SPOTLIGHT");

    private final String contentType;
    private final String slotKey;
    private final int schemaVersion;
    private final String sectionType;

    LobbyContentTuple(
            String contentType,
            String slotKey,
            int schemaVersion,
            String sectionType
    ) {
        this.contentType = contentType;
        this.slotKey = slotKey;
        this.schemaVersion = schemaVersion;
        this.sectionType = sectionType;
    }

    public String contentType() {
        return contentType;
    }

    public String slotKey() {
        return slotKey;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public String sectionType() {
        return sectionType;
    }

    public static Optional<LobbyContentTuple> find(
            String contentType,
            String slotKey,
            int schemaVersion
    ) {
        return Arrays.stream(values())
                .filter(tuple -> tuple.contentType.equals(contentType))
                .filter(tuple -> tuple.slotKey.equals(slotKey))
                .filter(tuple -> tuple.schemaVersion == schemaVersion)
                .findFirst();
    }
}
