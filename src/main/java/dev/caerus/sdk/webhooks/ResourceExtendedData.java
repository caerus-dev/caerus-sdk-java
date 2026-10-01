package dev.caerus.sdk.webhooks;

import java.util.Optional;

public record ResourceExtendedData(
        String eventId,
        String occurredOn,
        Optional<String> environmentId,
        Optional<String> apiKeyId,
        Optional<String> actorId,
        String holderId,
        long expiresAt) implements EventData {

    static ResourceExtendedData from(JsonFields fields) {
        return new ResourceExtendedData(
                fields.string("eventId"),
                fields.string("occurredOn"),
                fields.optionalString("environmentId"),
                fields.optionalString("apiKeyId"),
                fields.optionalString("actorId"),
                fields.string("holderId"),
                fields.longValue("expiresAt"));
    }
}
