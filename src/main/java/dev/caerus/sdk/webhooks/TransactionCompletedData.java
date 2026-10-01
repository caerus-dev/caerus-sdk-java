package dev.caerus.sdk.webhooks;

import java.util.Optional;

public record TransactionCompletedData(
        String eventId,
        String occurredOn,
        Optional<String> environmentId,
        Optional<String> apiKeyId,
        Optional<String> actorId,
        String transactionId) implements EventData {

    static TransactionCompletedData from(JsonFields fields) {
        return new TransactionCompletedData(
                fields.string("eventId"),
                fields.string("occurredOn"),
                fields.optionalString("environmentId"),
                fields.optionalString("apiKeyId"),
                fields.optionalString("actorId"),
                fields.string("transactionId"));
    }
}
