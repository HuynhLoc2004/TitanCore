package com.game.asset.storage;

public record StoredObjectMetadata(
        String mediaType,
        long byteSize,
        String checksum,
        String cacheControl
) {

    public boolean matches(ExpectedStoredObject expected) {
        return expected.mediaType().equals(mediaType)
                && expected.byteSize() == byteSize
                && expected.checksum().equals(checksum);
    }
}
