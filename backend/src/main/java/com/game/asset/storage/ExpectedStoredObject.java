package com.game.asset.storage;

import java.util.Locale;
import java.util.regex.Pattern;

public record ExpectedStoredObject(
        String mediaType,
        long byteSize,
        String checksum
) {

    private static final Pattern MEDIA_TYPE = Pattern.compile(
            "[a-z0-9][a-z0-9.+-]*/[a-z0-9][a-z0-9.+-]*"
    );
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ExpectedStoredObject {
        mediaType = normalize(mediaType);
        checksum = normalize(checksum);
        if (!MEDIA_TYPE.matcher(mediaType).matches()) {
            throw new IllegalArgumentException("Media type is not canonical");
        }
        if (byteSize <= 0) {
            throw new IllegalArgumentException("Byte size must be positive");
        }
        if (!SHA_256.matcher(checksum).matches()) {
            throw new IllegalArgumentException("Checksum must be lowercase SHA-256");
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
