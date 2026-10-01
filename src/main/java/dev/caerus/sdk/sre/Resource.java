package dev.caerus.sdk.sre;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public record Resource(
        String id,
        String key,
        String templateId,
        int availableAmount,
        int pendingCount,
        Optional<String> groupKey,
        Optional<Map<String, Object>> metadata,
        Optional<Instant> createdAt,
        Optional<Instant> updatedAt) {
}
