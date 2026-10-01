package dev.caerus.sdk.webhooks;

import java.util.Optional;

public record LockAcquireFailedData(
        String eventId,
        String occurredOn,
        Optional<String> environmentId,
        Optional<String> apiKeyId,
        Optional<String> actorId,
        String namespace,
        String lockKey,
        String transactionId,
        Optional<String> reason) implements EventData {

    static LockAcquireFailedData from(JsonFields fields) {
        return new LockAcquireFailedData(
                fields.string("eventId"),
                fields.string("occurredOn"),
                fields.optionalString("environmentId"),
                fields.optionalString("apiKeyId"),
                fields.optionalString("actorId"),
                fields.string("namespace"),
                fields.string("lockKey"),
                fields.string("transactionId"),
                fields.optionalString("reason"));
    }
}
