package dev.caerus.sdk.webhooks;

import java.util.Optional;

public record TransactionExpiredData(
        String eventId,
        String occurredOn,
        Optional<String> environmentId,
        Optional<String> apiKeyId,
        Optional<String> actorId,
        String transactionId,
        Optional<String> reason) implements EventData {

    static TransactionExpiredData from(JsonFields fields) {
        return new TransactionExpiredData(
                fields.string("eventId"),
                fields.string("occurredOn"),
                fields.optionalString("environmentId"),
                fields.optionalString("apiKeyId"),
                fields.optionalString("actorId"),
                fields.string("transactionId"),
                fields.optionalString("reason"));
    }
}
