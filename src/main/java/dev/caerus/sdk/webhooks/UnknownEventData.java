package dev.caerus.sdk.webhooks;

import java.util.Map;
import java.util.Optional;

public record UnknownEventData(
        String eventId,
        String occurredOn,
        Optional<String> environmentId,
        Optional<String> apiKeyId,
        Optional<String> actorId,
        Map<String, Object> fields) implements EventData {

    static UnknownEventData from(JsonFields fields) {
        return new UnknownEventData(
                fields.string("eventId"),
                fields.string("occurredOn"),
                fields.optionalString("environmentId"),
                fields.optionalString("apiKeyId"),
                fields.optionalString("actorId"),
                fields.asMap());
    }
}
