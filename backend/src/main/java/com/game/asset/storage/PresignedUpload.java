package com.game.asset.storage;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class PresignedUpload {

    private final URI uri;
    private final Instant expiresAt;
    private final Map<String, List<String>> requiredHeaders;

    public PresignedUpload(
            URI uri,
            Instant expiresAt,
            Map<String, List<String>> requiredHeaders
    ) {
        this.uri = uri;
        this.expiresAt = expiresAt;
        this.requiredHeaders = requiredHeaders.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())
                ));
    }

    public URI uri() {
        return uri;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Map<String, List<String>> requiredHeaders() {
        return Map.copyOf(requiredHeaders);
    }

    @Override
    public String toString() {
        return "PresignedUpload[REDACTED]";
    }
}
