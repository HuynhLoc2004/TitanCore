package com.game.asset.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageObjectKeyTests {

    private static final String CHECKSUM =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void createsCanonicalContentHashedKey() {
        StorageObjectKey key = StorageObjectKey.create(
                "IMAGE",
                CHECKSUM.toUpperCase(java.util.Locale.ROOT),
                "HERO-MOBILE",
                "WEBP"
        );

        assertThat(key.value()).isEqualTo(
                "assets/image/01/" + CHECKSUM + "/hero-mobile.webp"
        );
        assertThat(StorageObjectKey.parse(key.value())).isEqualTo(key);
    }

    @Test
    void rejectsTraversalUrlsAndUnsupportedFormats() {
        assertThatThrownBy(() -> StorageObjectKey.parse("../private/key"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageObjectKey.parse(
                "https://example.com/assets/image/file.png"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageObjectKey.create(
                "IMAGE", CHECKSUM, "hero", "svg"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedChecksumAndVariant() {
        assertThatThrownBy(() -> StorageObjectKey.create(
                "IMAGE", "not-a-checksum", "hero", "png"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageObjectKey.create(
                "IMAGE", CHECKSUM, "../../hero", "png"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageObjectKey.create(
                "IMAGE", CHECKSUM, "hero mobile", "png"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
