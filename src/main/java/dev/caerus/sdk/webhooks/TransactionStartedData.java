package dev.caerus.sdk.webhooks;

import java.util.Optional;

public record TransactionStartedData(
        String eventId,
        String occurredOn,
        Optional<String> environmentId,
        Optional<String> apiKeyId,
        Optional<String> actorId,
        String transactionId,
        long transactionTimeoutMs) implements EventData {

    static TransactionStartedData from(JsonFields fields) {
        return new TransactionStartedData(
                fields.string("eventId"),
                fields.string("occurredOn"),
                fields.optionalString("environmentId"),
                fields.optionalString("apiKeyId"),
                fields.optionalString("actorId"),
                fields.string("transactionId"),
                fields.longValue("transactionTimeoutMs"));
    }
}
