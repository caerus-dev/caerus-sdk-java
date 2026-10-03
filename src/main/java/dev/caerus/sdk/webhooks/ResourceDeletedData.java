package dev.caerus.sdk.webhooks;

import java.util.Optional;

public record ResourceDeletedData(
        String eventId,
        String occurredOn,
        Optional<String> environmentId,
        Optional<String> apiKeyId,
        Optional<String> actorId,
        String resourceKey) implements EventData {

    static ResourceDeletedData from(JsonFields fields) {
        return new ResourceDeletedData(
                fields.string("eventId"),
                fields.string("occurredOn"),
                fields.optionalString("environmentId"),
                fields.optionalString("apiKeyId"),
                fields.optionalString("actorId"),
                fields.string("resourceKey"));
    }
}
