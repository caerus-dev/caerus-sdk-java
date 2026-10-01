package dev.caerus.sdk.webhooks;

import java.util.Optional;

public record ResourceTakeFailedData(
        String eventId,
        String occurredOn,
        Optional<String> environmentId,
        Optional<String> apiKeyId,
        Optional<String> actorId,
        Optional<String> resourceId,
        String resourceKey,
        int amount,
        Optional<String> reason,
        long createdAtMs) implements EventData {

    static ResourceTakeFailedData from(JsonFields fields) {
        return new ResourceTakeFailedData(
                fields.string("eventId"),
                fields.string("occurredOn"),
                fields.optionalString("environmentId"),
                fields.optionalString("apiKeyId"),
                fields.optionalString("actorId"),
                fields.optionalString("resourceId"),
                fields.string("resourceKey"),
                fields.integer("amount"),
                fields.optionalString("reason"),
                fields.longValue("createdAtMs"));
    }
}
