package dev.caerus.sdk.webhooks;

import java.util.Map;

public record CaerusEvent(
        String id,
        String eventId,
        String product,
        String objectType,
        String objectId,
        String environmentId,
        String occurredAt,
        String eventType,
        EventData data,
        Map<String, Object> raw) {
}
