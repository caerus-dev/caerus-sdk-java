package dev.caerus.sdk.webhooks;

import java.util.Optional;

public record LockAcquiredData(
        String eventId,
        String occurredOn,
        Optional<String> environmentId,
        Optional<String> apiKeyId,
        Optional<String> actorId,
        String namespace,
        String lockKey,
        String lockId,
        String transactionId,
        long fencingToken,
        String mode) implements EventData {

    static LockAcquiredData from(JsonFields fields) {
        return new LockAcquiredData(
                fields.string("eventId"),
                fields.string("occurredOn"),
                fields.optionalString("environmentId"),
                fields.optionalString("apiKeyId"),
                fields.optionalString("actorId"),
                fields.string("namespace"),
                fields.string("lockKey"),
                fields.string("lockId"),
                fields.string("transactionId"),
                fields.longValue("fencingToken"),
                fields.string("mode"));
    }
}
