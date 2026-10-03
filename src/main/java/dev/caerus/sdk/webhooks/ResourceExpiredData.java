package dev.caerus.sdk.webhooks;

import java.util.Optional;

public record ResourceExpiredData(
        String eventId,
        String occurredOn,
        Optional<String> environmentId,
        Optional<String> apiKeyId,
        Optional<String> actorId,
        String holderId,
        String resourceKey,
        int amount,
        long expiresAt,
        long createdAtMs) implements EventData {

    static ResourceExpiredData from(JsonFields fields) {
        return new ResourceExpiredData(
                fields.string("eventId"),
                fields.string("occurredOn"),
                fields.optionalString("environmentId"),
                fields.optionalString("apiKeyId"),
                fields.optionalString("actorId"),
                fields.string("holderId"),
                fields.string("resourceKey"),
                fields.integer("amount"),
                fields.longValue("expiresAt"),
                fields.longValue("createdAtMs"));
    }
}
