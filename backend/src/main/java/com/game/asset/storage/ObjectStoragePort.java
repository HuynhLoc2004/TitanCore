package com.game.asset.storage;

import java.time.Duration;

public interface ObjectStoragePort {

    PresignedUpload presignPrivateUpload(
            StorageObjectKey key,
            ExpectedStoredObject expected,
            Duration ttl
    );

    StoredObjectMetadata inspect(StorageZone zone, StorageObjectKey key);

    void copyPrivateToPublished(
            StorageObjectKey key,
            ExpectedStoredObject expected
    );
}
