package dev.caerus.sdk.webhooks;

import java.util.List;
import java.util.Optional;

public record DeadlockDetectedData(
        String eventId,
        String occurredOn,
        Optional<String> environmentId,
        Optional<String> apiKeyId,
        Optional<String> actorId,
        String victimTransactionId,
        List<String> cycleTransactionIds,
        String resolutionStrategy,
        Optional<String> reason) implements EventData {

    static DeadlockDetectedData from(JsonFields fields) {
        return new DeadlockDetectedData(
                fields.string("eventId"),
                fields.string("occurredOn"),
                fields.optionalString("environmentId"),
                fields.optionalString("apiKeyId"),
                fields.optionalString("actorId"),
                fields.string("victimTransactionId"),
                fields.stringList("cycleTransactionIds"),
                fields.string("resolutionStrategy"),
                fields.optionalString("reason"));
    }
}
