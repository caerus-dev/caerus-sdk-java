package dev.caerus.sdk.webhooks;

import java.util.List;
import java.util.Optional;

public record LockAbandonedData(
        String eventId,
        String occurredOn,
        Optional<String> environmentId,
        Optional<String> apiKeyId,
        Optional<String> actorId,
        String transactionId,
        List<String> lockIds,
        Optional<String> reason) implements EventData {

    static LockAbandonedData from(JsonFields fields) {
        return new LockAbandonedData(
                fields.string("eventId"),
                fields.string("occurredOn"),
                fields.optionalString("environmentId"),
                fields.optionalString("apiKeyId"),
                fields.optionalString("actorId"),
                fields.string("transactionId"),
                fields.stringList("lockIds"),
                fields.optionalString("reason"));
    }
}
