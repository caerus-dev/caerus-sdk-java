package dev.caerus.sdk.webhooks;

import java.util.Optional;

public record ResourceReleasedData(
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

    static ResourceReleasedData from(JsonFields fields) {
        return new ResourceReleasedData(
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
