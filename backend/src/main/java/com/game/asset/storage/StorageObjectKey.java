package com.game.asset.storage;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class StorageObjectKey {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern KEY_SEGMENT = Pattern.compile("[a-z0-9][a-z0-9-]{0,23}");
    private static final Pattern KEY = Pattern.compile(
            "assets/(image|audio|font)/[0-9a-f]{2}/[0-9a-f]{64}/"
                    + "[a-z0-9][a-z0-9-]{0,23}\\.(avif|webp|png|jpg|ogg|mp3|wav|woff2)"
    );
    private static final Set<String> EXTENSIONS = Set.of(
            "avif", "webp", "png", "jpg", "ogg", "mp3", "wav", "woff2"
    );

    private final String value;

    private StorageObjectKey(String value) {
        this.value = value;
    }

    public static StorageObjectKey parse(String value) {
        if (value == null || !KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("Object key is not canonical");
        }
        return new StorageObjectKey(value);
    }

    public static StorageObjectKey create(
            String mediaCategory,
            String checksum,
            String variant,
            String extension
    ) {
        String category = normalize(mediaCategory);
        String normalizedChecksum = normalize(checksum);
        String normalizedVariant = normalize(variant);
        String normalizedExtension = normalize(extension);

        if (!Set.of("image", "audio", "font").contains(category)) {
            throw new IllegalArgumentException("Media category is not supported");
        }
        if (!SHA_256.matcher(normalizedChecksum).matches()) {
            throw new IllegalArgumentException("Checksum must be lowercase SHA-256");
        }
        if (!KEY_SEGMENT.matcher(normalizedVariant).matches()) {
            throw new IllegalArgumentException("Variant is not canonical");
        }
        if (!EXTENSIONS.contains(normalizedExtension)) {
            throw new IllegalArgumentException("Extension is not supported");
        }

        return parse("assets/" + category + "/" + normalizedChecksum.substring(0, 2)
                + "/" + normalizedChecksum + "/" + normalizedVariant + "."
                + normalizedExtension);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StorageObjectKey key && value.equals(key.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
