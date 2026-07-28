package com.game.asset.storage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class S3ObjectStorageAdapterIntegrationTests {

    private static final String ACCOUNT = "testapplicationaccess";
    private static final String CREDENTIAL_MATERIAL = "test-credential-material-12345678";
    private static final byte[] CONTENT = "verified-titancore-asset"
            .getBytes(StandardCharsets.UTF_8);
    private static final String CHECKSUM = sha256(CONTENT);

    @Container
    private static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("minio/minio:RELEASE.2025-04-22T22-12-26Z")
    )
            .withEnv("MINIO_ROOT_USER", ACCOUNT)
            .withEnv("MINIO_ROOT_PASSWORD", CREDENTIAL_MATERIAL)
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000))
            .withStartupTimeout(Duration.ofMinutes(2));

    private static S3Client client;
    private static S3Presigner presigner;
    private static S3ObjectStorageAdapter adapter;

    @BeforeAll
    static void setUp() {
        URI endpoint = URI.create("http://" + MINIO.getHost() + ":"
                + MINIO.getMappedPort(9000));
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(ACCOUNT, CREDENTIAL_MATERIAL)
        );
        S3Configuration service = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
        client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials)
                .serviceConfiguration(service)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(2))
                        .socketTimeout(Duration.ofSeconds(5)))
                .build();
        presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials)
                .serviceConfiguration(service)
                .build();

        AssetStorageProperties properties =
                AssetStoragePropertiesValidatorTests.validProperties();
        properties.setEndpoint(endpoint.toString());
        adapter = new S3ObjectStorageAdapter(client, presigner, properties);

        client.createBucket(CreateBucketRequest.builder()
                .bucket(properties.getPrivateBucket())
                .build());
        client.createBucket(CreateBucketRequest.builder()
                .bucket(properties.getPublicBucket())
                .build());
    }

    @AfterAll
    static void closeClients() {
        if (presigner != null) {
            presigner.close();
        }
        if (client != null) {
            client.close();
        }
    }

    @Test
    void uploadsInspectsAndPublishesImmutableObject() throws Exception {
        StorageObjectKey key = StorageObjectKey.create(
                "IMAGE", CHECKSUM, "hero", "webp"
        );
        ExpectedStoredObject expected = new ExpectedStoredObject(
                "image/webp", CONTENT.length, CHECKSUM
        );

        PresignedUpload upload = adapter.presignPrivateUpload(
                key, expected, Duration.ofMinutes(2)
        );
        assertThat(upload.toString()).doesNotContain(upload.uri().toString());
        assertThat(put(upload, CONTENT)).isIn(200, 204);

        assertThat(adapter.inspect(StorageZone.PRIVATE, key).matches(expected)).isTrue();
        assertThatThrownBy(() -> adapter.inspect(StorageZone.PUBLISHED, key))
                .isInstanceOfSatisfying(
                        ObjectStorageException.class,
                        exception -> assertThat(exception.isNotFound()).isTrue()
                );

        adapter.copyPrivateToPublished(key, expected);
        StoredObjectMetadata published = adapter.inspect(StorageZone.PUBLISHED, key);
        assertThat(published.matches(expected)).isTrue();
        assertThat(published.cacheControl())
                .isEqualTo("public, max-age=31536000, immutable");

        adapter.copyPrivateToPublished(key, expected);
        assertThat(adapter.inspect(StorageZone.PUBLISHED, key).matches(expected)).isTrue();
    }

    @Test
    void rejectsMetadataMismatchBeforePublishing() throws Exception {
        byte[] content = "different-asset".getBytes(StandardCharsets.UTF_8);
        String checksum = sha256(content);
        StorageObjectKey key = StorageObjectKey.create(
                "AUDIO", checksum, "medium", "ogg"
        );
        ExpectedStoredObject expected = new ExpectedStoredObject(
                "audio/ogg", content.length, checksum
        );
        assertThat(put(adapter.presignPrivateUpload(
                key, expected, Duration.ofMinutes(2)
        ), content)).isIn(200, 204);

        ExpectedStoredObject wrong = new ExpectedStoredObject(
                "audio/ogg", content.length + 1L, checksum
        );
        assertThatThrownBy(() -> adapter.copyPrivateToPublished(key, wrong))
                .isInstanceOf(ObjectStorageException.class)
                .hasMessage("Object storage metadata-verification failed with status 409")
                .hasMessageNotContaining(key.value())
                .hasMessageNotContaining("titancore-private");
    }

    @Test
    void signedHeadersAreRequiredForUpload() throws Exception {
        byte[] content = "signed-header-check".getBytes(StandardCharsets.UTF_8);
        String checksum = sha256(content);
        StorageObjectKey key = StorageObjectKey.create(
                "IMAGE", checksum, "card", "png"
        );
        ExpectedStoredObject expected = new ExpectedStoredObject(
                "image/png", content.length, checksum
        );
        PresignedUpload upload = adapter.presignPrivateUpload(
                key, expected, Duration.ofMinutes(2)
        );

        HttpRequest request = HttpRequest.newBuilder(upload.uri())
                .header("Content-Type", "image/jpeg")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        HttpResponse<Void> response = HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.discarding()
        );

        assertThat(response.statusCode()).isBetween(400, 499);
        assertThatThrownBy(() -> adapter.inspect(StorageZone.PRIVATE, key))
                .isInstanceOf(ObjectStorageException.class);
    }

    @Test
    void rejectsPayloadThatDoesNotMatchSignedChecksum() throws Exception {
        byte[] expectedContent = "checksum-expected".getBytes(StandardCharsets.UTF_8);
        byte[] wrongContent = "checksum-tampered".getBytes(StandardCharsets.UTF_8);
        String checksum = sha256(expectedContent);
        StorageObjectKey key = StorageObjectKey.create(
                "IMAGE", checksum, "checksum", "webp"
        );
        ExpectedStoredObject expected = new ExpectedStoredObject(
                "image/webp", expectedContent.length, checksum
        );

        assertThat(wrongContent).hasSameSizeAs(expectedContent);
        assertThat(put(adapter.presignPrivateUpload(
                key, expected, Duration.ofMinutes(2)
        ), wrongContent)).isBetween(400, 499);
        assertThatThrownBy(() -> adapter.inspect(StorageZone.PRIVATE, key))
                .isInstanceOf(ObjectStorageException.class);
    }

    @Test
    void refusesToOverwriteContentAddressedObject() throws Exception {
        byte[] content = "immutable-object".getBytes(StandardCharsets.UTF_8);
        String checksum = sha256(content);
        StorageObjectKey key = StorageObjectKey.create(
                "IMAGE", checksum, "immutable", "png"
        );
        ExpectedStoredObject expected = new ExpectedStoredObject(
                "image/png", content.length, checksum
        );

        assertThat(put(adapter.presignPrivateUpload(
                key, expected, Duration.ofMinutes(2)
        ), content)).isIn(200, 204);
        assertThat(put(adapter.presignPrivateUpload(
                key, expected, Duration.ofMinutes(2)
        ), content)).isBetween(400, 499);
        assertThat(adapter.inspect(StorageZone.PRIVATE, key).matches(expected)).isTrue();
    }

    private static int put(PresignedUpload upload, byte[] content) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(upload.uri());
        for (Map.Entry<String, List<String>> header : upload.requiredHeaders().entrySet()) {
            if (!"host".equalsIgnoreCase(header.getKey())
                    && !"content-length".equalsIgnoreCase(header.getKey())) {
                for (String value : header.getValue()) {
                    request.header(header.getKey(), value);
                }
            }
        }
        HttpResponse<Void> response = HttpClient.newHttpClient().send(
                request.PUT(HttpRequest.BodyPublishers.ofByteArray(content)).build(),
                HttpResponse.BodyHandlers.discarding()
        );
        return response.statusCode();
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content)
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
