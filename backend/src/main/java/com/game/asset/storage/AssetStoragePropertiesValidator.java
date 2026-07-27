package com.game.asset.storage;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AssetStoragePropertiesValidator {

    private static final Pattern BUCKET = Pattern.compile(
            "[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"
    );

    private AssetStoragePropertiesValidator() {
    }

    public static void validate(AssetStorageProperties properties, boolean production) {
        if (production && !properties.isEnabled()) {
            throw new IllegalStateException("Object storage must be enabled in production");
        }
        if (!properties.isEnabled()) {
            return;
        }

        requireText(properties.getRegion(), "OBJECT_STORAGE_REGION");
        requireText(properties.getAccessKey(), "OBJECT_STORAGE_ACCESS_KEY");
        requireText(properties.getSecretKey(), "OBJECT_STORAGE_SECRET_KEY");
        requireUnpadded(properties.getAccessKey(), "OBJECT_STORAGE_ACCESS_KEY");
        requireUnpadded(properties.getSecretKey(), "OBJECT_STORAGE_SECRET_KEY");
        validateBucket(properties.getPrivateBucket(), "OBJECT_STORAGE_PRIVATE_BUCKET");
        validateBucket(properties.getPublicBucket(), "OBJECT_STORAGE_PUBLIC_BUCKET");
        if (properties.getPrivateBucket().equals(properties.getPublicBucket())) {
            throw new IllegalStateException("Private and published buckets must differ");
        }

        URI endpoint = validateUri(properties.getEndpoint(), "OBJECT_STORAGE_ENDPOINT");
        URI publicBase = validateUri(properties.getPublicBaseUrl(), "ASSET_PUBLIC_BASE_URL");
        if (StringUtils.hasText(endpoint.getPath()) && !"/".equals(endpoint.getPath())) {
            throw new IllegalStateException("Object storage endpoint must not contain a path");
        }
        validatePublicPath(publicBase);
        if (production && !"https".equals(endpoint.getScheme())) {
            throw new IllegalStateException("Object storage endpoint must use HTTPS in production");
        }
        if (production && !"https".equals(publicBase.getScheme())) {
            throw new IllegalStateException("Asset public base URL must use HTTPS in production");
        }
        if (!production && (!isLocalHttp(endpoint) || !isLocalHttp(publicBase))) {
            requireHttps(endpoint, "Object storage endpoint");
            requireHttps(publicBase, "Asset public base URL");
        }

        validateDuration(properties.getConnectTimeout(), Duration.ofMillis(100),
                Duration.ofSeconds(10), "Object storage connect timeout");
        validateDuration(properties.getReadTimeout(), Duration.ofMillis(500),
                Duration.ofSeconds(30), "Object storage read timeout");
        validateDuration(properties.getPresignTtl(), Duration.ofSeconds(30),
                Duration.ofMinutes(15), "Object storage presign TTL");

        if (production && properties.getAccessKey().length() < 16) {
            throw new IllegalStateException("Object storage access key is too short for production");
        }
        if (production && properties.getSecretKey().length() < 32) {
            throw new IllegalStateException("Object storage secret key is too short for production");
        }
    }

    public static URI endpoint(AssetStorageProperties properties) {
        return validateUri(properties.getEndpoint(), "OBJECT_STORAGE_ENDPOINT");
    }

    private static void validateBucket(String value, String variable) {
        requireText(value, variable);
        if (!BUCKET.matcher(value).matches()
                || value.contains("..")
                || value.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            throw new IllegalStateException(variable + " is not a canonical bucket name");
        }
    }

    private static URI validateUri(String value, String variable) {
        requireText(value, variable);
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null
                    ? ""
                    : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!uri.isAbsolute()
                    || uri.getHost() == null
                    || !SetHolder.SCHEMES.contains(scheme)
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new IllegalStateException(variable + " must be a safe absolute URI");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(variable + " is malformed", exception);
        }
    }

    private static boolean isLocalHttp(URI uri) {
        if (!"http".equals(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "minio".equalsIgnoreCase(host);
    }

    private static void requireHttps(URI uri, String label) {
        if (!"https".equals(uri.getScheme())) {
            throw new IllegalStateException(label + " must use HTTPS");
        }
    }

    private static void validatePublicPath(URI uri) {
        String path = uri.getRawPath() == null
                ? ""
                : uri.getRawPath().toLowerCase(Locale.ROOT);
        if (path.contains("..")
                || path.contains("\\")
                || path.contains("//")
                || path.contains("%2e")
                || path.contains("%2f")
                || path.contains("%5c")) {
            throw new IllegalStateException("Asset public base URL contains an unsafe path");
        }
    }

    private static void validateDuration(
            Duration value,
            Duration minimum,
            Duration maximum,
            String label
    ) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalStateException(label + " is outside the allowed range");
        }
    }

    private static void requireText(String value, String variable) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(variable + " is required when object storage is enabled");
        }
    }

    private static void requireUnpadded(String value, String variable) {
        if (!value.equals(value.trim())) {
            throw new IllegalStateException(variable + " must not contain surrounding whitespace");
        }
    }

    private static final class SetHolder {
        private static final java.util.Set<String> SCHEMES = java.util.Set.of("http", "https");

        private SetHolder() {
        }
    }
}
