package dev.caerus.sdk.sre;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public record ResourceHolder(
        String id,
        String resourceId,
        ResourceHolderStatus status,
        int amount,
        Instant expiresAt,
        Optional<Map<String, Object>> metadata,
        Optional<Instant> createdAt) {
}
