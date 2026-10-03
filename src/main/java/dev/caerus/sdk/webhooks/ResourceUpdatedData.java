package dev.caerus.sdk.webhooks;

import java.util.Optional;

public record ResourceUpdatedData(
        String eventId,
        String occurredOn,
        Optional<String> environmentId,
        Optional<String> apiKeyId,
        Optional<String> actorId,
        String resourceKey,
        Optional<Integer> deltaAmount,
        Optional<String> groupKey,
        Optional<String> metadata,
        long updatedAtMs) implements EventData {

    static ResourceUpdatedData from(JsonFields fields) {
        return new ResourceUpdatedData(
                fields.string("eventId"),
                fields.string("occurredOn"),
                fields.optionalString("environmentId"),
                fields.optionalString("apiKeyId"),
                fields.optionalString("actorId"),
                fields.string("resourceKey"),
                fields.optionalInteger("deltaAmount"),
                fields.optionalString("groupKey"),
                fields.optionalString("metadata"),
                fields.longValue("updatedAtMs"));
    }
}
