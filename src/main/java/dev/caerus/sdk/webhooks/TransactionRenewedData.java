package dev.caerus.sdk.webhooks;

import java.util.Optional;

public record TransactionRenewedData(
        String eventId,
        String occurredOn,
        Optional<String> environmentId,
        Optional<String> apiKeyId,
        Optional<String> actorId,
        String transactionId,
        long newLifetimeMs) implements EventData {

    static TransactionRenewedData from(JsonFields fields) {
        return new TransactionRenewedData(
                fields.string("eventId"),
                fields.string("occurredOn"),
                fields.optionalString("environmentId"),
                fields.optionalString("apiKeyId"),
                fields.optionalString("actorId"),
                fields.string("transactionId"),
                fields.longValue("newLifetimeMs"));
    }
}
