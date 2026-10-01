package dev.caerus.sdk.webhooks;

import java.util.Optional;

public record ResourceQueuedData(
        String eventId,
        String occurredOn,
        Optional<String> environmentId,
        Optional<String> apiKeyId,
        Optional<String> actorId,
        String holderId,
        String resourceKey,
        int amount,
        long expiresAt,
        long createdAtMs,
        Optional<String> metadata,
        Optional<String> idempotencyKey) implements EventData {

    static ResourceQueuedData from(JsonFields fields) {
        return new ResourceQueuedData(
                fields.string("eventId"),
                fields.string("occurredOn"),
                fields.optionalString("environmentId"),
                fields.optionalString("apiKeyId"),
                fields.optionalString("actorId"),
                fields.string("holderId"),
                fields.string("resourceKey"),
                fields.integer("amount"),
                fields.longValue("expiresAt"),
                fields.longValue("createdAtMs"),
                fields.optionalString("metadata"),
                fields.optionalString("idempotencyKey"));
    }
}
