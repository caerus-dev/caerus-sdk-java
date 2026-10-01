package dev.caerus.sdk.webhooks;

import java.util.Optional;

public record LockReleasedData(
        String eventId,
        String occurredOn,
        Optional<String> environmentId,
        Optional<String> apiKeyId,
        Optional<String> actorId,
        String namespace,
        String lockKey,
        String lockId,
        String transactionId) implements EventData {

    static LockReleasedData from(JsonFields fields) {
        return new LockReleasedData(
                fields.string("eventId"),
                fields.string("occurredOn"),
                fields.optionalString("environmentId"),
                fields.optionalString("apiKeyId"),
                fields.optionalString("actorId"),
                fields.string("namespace"),
                fields.string("lockKey"),
                fields.string("lockId"),
                fields.string("transactionId"));
    }
}
