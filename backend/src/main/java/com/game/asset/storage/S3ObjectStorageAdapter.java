package com.game.asset.storage;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

public final class S3ObjectStorageAdapter implements ObjectStoragePort {

    private static final String CHECKSUM_METADATA = "sha256";
    private static final String IMMUTABLE_CACHE_CONTROL =
            "public, max-age=31536000, immutable";

    private final S3Client client;
    private final S3Presigner presigner;
    private final AssetStorageProperties properties;
    private final Clock clock;

    public S3ObjectStorageAdapter(
            S3Client client,
            S3Presigner presigner,
            AssetStorageProperties properties
    ) {
        this(client, presigner, properties, Clock.systemUTC());
    }

    S3ObjectStorageAdapter(
            S3Client client,
            S3Presigner presigner,
            AssetStorageProperties properties,
            Clock clock
    ) {
        this.client = client;
        this.presigner = presigner;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public PresignedUpload presignPrivateUpload(
            StorageObjectKey key,
            ExpectedStoredObject expected,
            Duration ttl
    ) {
        validateTtl(ttl);
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(properties.getPrivateBucket())
                .key(key.value())
                .contentType(expected.mediaType())
                .contentLength(expected.byteSize())
                .checksumSHA256(Base64.getEncoder().encodeToString(
                        HexFormat.of().parseHex(expected.checksum())
                ))
                .ifNoneMatch("*")
                .metadata(Map.of(CHECKSUM_METADATA, expected.checksum()))
                .build();
        try {
            PresignedPutObjectRequest request = presigner.presignPutObject(
                    PutObjectPresignRequest.builder()
                            .signatureDuration(ttl)
                            .putObjectRequest(putRequest)
                            .build()
            );
            return new PresignedUpload(
                    request.url().toURI(),
                    Instant.now(clock).plus(ttl),
                    request.signedHeaders()
            );
        } catch (SdkException | java.net.URISyntaxException exception) {
            throw ObjectStorageException.failed("presign", statusCode(exception));
        }
    }

    @Override
    public StoredObjectMetadata inspect(StorageZone zone, StorageObjectKey key) {
        String bucket = bucket(zone);
        try {
            HeadObjectResponse response = client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key.value())
                    .build());
            return new StoredObjectMetadata(
                    normalizeMediaType(response.contentType()),
                    response.contentLength(),
                    response.metadata().getOrDefault(CHECKSUM_METADATA, ""),
                    response.cacheControl() == null ? "" : response.cacheControl()
            );
        } catch (NoSuchKeyException exception) {
            throw ObjectStorageException.notFound("inspect");
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw ObjectStorageException.notFound("inspect");
            }
            throw ObjectStorageException.failed("inspect", exception.statusCode());
        } catch (SdkException exception) {
            throw ObjectStorageException.failed("inspect", null);
        }
    }

    @Override
    public void copyPrivateToPublished(
            StorageObjectKey key,
            ExpectedStoredObject expected
    ) {
        requireMatch(inspect(StorageZone.PRIVATE, key), expected);
        StoredObjectMetadata published = inspectIfPresent(StorageZone.PUBLISHED, key);
        if (published != null) {
            requirePublishedMatch(published, expected);
            return;
        }

        try {
            client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(properties.getPrivateBucket())
                    .sourceKey(key.value())
                    .destinationBucket(properties.getPublicBucket())
                    .destinationKey(key.value())
                    .metadataDirective(MetadataDirective.REPLACE)
                    .contentType(expected.mediaType())
                    .cacheControl(IMMUTABLE_CACHE_CONTROL)
                    .metadata(Map.of(CHECKSUM_METADATA, expected.checksum()))
                    .build());
        } catch (S3Exception exception) {
            throw ObjectStorageException.failed("publish-copy", exception.statusCode());
        } catch (SdkException exception) {
            throw ObjectStorageException.failed("publish-copy", null);
        }
        requirePublishedMatch(inspect(StorageZone.PUBLISHED, key), expected);
    }

    private StoredObjectMetadata inspectIfPresent(
            StorageZone zone,
            StorageObjectKey key
    ) {
        try {
            return inspect(zone, key);
        } catch (ObjectStorageException exception) {
            if (exception.isNotFound()) {
                return null;
            }
            throw exception;
        }
    }

    private void requireMatch(
            StoredObjectMetadata actual,
            ExpectedStoredObject expected
    ) {
        if (!actual.matches(expected)) {
            throw ObjectStorageException.failed("metadata-verification", 409);
        }
    }

    private void requirePublishedMatch(
            StoredObjectMetadata actual,
            ExpectedStoredObject expected
    ) {
        requireMatch(actual, expected);
        if (!IMMUTABLE_CACHE_CONTROL.equals(actual.cacheControl())) {
            throw ObjectStorageException.failed("published-cache-verification", 409);
        }
    }

    private String bucket(StorageZone zone) {
        return zone == StorageZone.PRIVATE
                ? properties.getPrivateBucket()
                : properties.getPublicBucket();
    }

    private void validateTtl(Duration ttl) {
        if (ttl == null
                || ttl.compareTo(Duration.ofSeconds(30)) < 0
                || ttl.compareTo(properties.getPresignTtl()) > 0) {
            throw new IllegalArgumentException("Presign TTL is outside the allowed range");
        }
    }

    private String normalizeMediaType(String mediaType) {
        if (mediaType == null) {
            return "";
        }
        int parameters = mediaType.indexOf(';');
        return (parameters < 0 ? mediaType : mediaType.substring(0, parameters))
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
    }

    private Integer statusCode(Exception exception) {
        return exception instanceof S3Exception s3Exception
                ? s3Exception.statusCode()
                : null;
    }
}
