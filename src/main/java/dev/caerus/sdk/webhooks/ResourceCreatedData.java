package dev.caerus.sdk.webhooks;

import java.util.Optional;

public record ResourceCreatedData(
        String eventId,
        String occurredOn,
        Optional<String> environmentId,
        Optional<String> apiKeyId,
        Optional<String> actorId,
        String resourceKey,
        Optional<String> templateId,
        int availableAmount,
        Optional<String> groupKey,
        Optional<String> metadata,
        long createdAtMs) implements EventData {

    static ResourceCreatedData from(JsonFields fields) {
        return new ResourceCreatedData(
                fields.string("eventId"),
                fields.string("occurredOn"),
                fields.optionalString("environmentId"),
                fields.optionalString("apiKeyId"),
                fields.optionalString("actorId"),
                fields.string("resourceKey"),
                fields.optionalString("templateId"),
                fields.integer("availableAmount"),
                fields.optionalString("groupKey"),
                fields.optionalString("metadata"),
                fields.longValue("createdAtMs"));
    }
}
