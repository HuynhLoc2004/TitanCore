package com.game.asset.storage;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetStoragePropertiesValidatorTests {

    @Test
    void disabledStorageIsAllowedOutsideProduction() {
        AssetStorageProperties properties = new AssetStorageProperties();

        assertThatCode(() -> AssetStoragePropertiesValidator.validate(properties, false))
                .doesNotThrowAnyException();
    }

    @Test
    void productionCannotDisableStorage() {
        AssetStorageProperties properties = new AssetStorageProperties();

        assertThatThrownBy(() -> AssetStoragePropertiesValidator.validate(properties, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Object storage must be enabled in production");
    }

    @Test
    void acceptsLocalMinioConfiguration() {
        AssetStorageProperties properties = validProperties();

        assertThatCode(() -> AssetStoragePropertiesValidator.validate(properties, false))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSharedPrivateAndPublishedBucket() {
        AssetStorageProperties properties = validProperties();
        properties.setPublicBucket(properties.getPrivateBucket());

        assertThatThrownBy(() -> AssetStoragePropertiesValidator.validate(properties, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Private and published buckets must differ");
    }

    @Test
    void rejectsInsecureRemoteEndpoint() {
        AssetStorageProperties properties = validProperties();
        properties.setEndpoint("http://storage.example.com");

        assertThatThrownBy(() -> AssetStoragePropertiesValidator.validate(properties, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Object storage endpoint must use HTTPS");
    }

    @Test
    void productionRequiresHttpsAndStrongCredentials() {
        AssetStorageProperties properties = validProperties();
        properties.setEndpoint("https://storage.example.com");
        properties.setPublicBaseUrl("https://assets.example.com");
        properties.setAccessKey("short");

        assertThatThrownBy(() -> AssetStoragePropertiesValidator.validate(properties, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Object storage access key is too short for production");
    }

    @Test
    void rejectsCredentialsEmbeddedInUrlAndUnsafeDurations() {
        AssetStorageProperties embeddedCredentials = validProperties();
        embeddedCredentials.setEndpoint("https://user@example.com");

        assertThatThrownBy(() -> AssetStoragePropertiesValidator.validate(
                embeddedCredentials, false
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("safe absolute URI");

        AssetStorageProperties invalidTtl = validProperties();
        invalidTtl.setPresignTtl(Duration.ofHours(1));
        assertThatThrownBy(() -> AssetStoragePropertiesValidator.validate(
                invalidTtl, false
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("presign TTL");
    }

    @Test
    void rejectsEndpointPathsAndEncodedPublicTraversal() {
        AssetStorageProperties endpointPath = validProperties();
        endpointPath.setEndpoint("https://storage.example.com/api");
        assertThatThrownBy(() -> AssetStoragePropertiesValidator.validate(
                endpointPath, false
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Object storage endpoint must not contain a path");

        AssetStorageProperties publicTraversal = validProperties();
        publicTraversal.setPublicBaseUrl("https://assets.example.com/%2e%2e/private");
        assertThatThrownBy(() -> AssetStoragePropertiesValidator.validate(
                publicTraversal, false
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Asset public base URL contains an unsafe path");
    }

    static AssetStorageProperties validProperties() {
        AssetStorageProperties properties = new AssetStorageProperties();
        properties.setEnabled(true);
        properties.setEndpoint("http://localhost:9000");
        properties.setRegion("us-east-1");
        properties.setPrivateBucket("titancore-private");
        properties.setPublicBucket("titancore-published");
        properties.setAccessKey("local-application-access");
        properties.setSecretKey("local-application-credential-material");
        properties.setPathStyle(true);
        properties.setPublicBaseUrl("http://localhost:9000/titancore-published");
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(5));
        properties.setPresignTtl(Duration.ofMinutes(5));
        return properties;
    }
}
