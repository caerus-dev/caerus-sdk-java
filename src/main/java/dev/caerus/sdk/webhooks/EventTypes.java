package dev.caerus.sdk.webhooks;

final class EventTypes {

    private EventTypes() {
    }

    static EventData decode(String eventType, JsonFields fields) {
        if (eventType == null) {
            return UnknownEventData.from(fields);
        }
        switch (eventType) {
            case "resource.created":
                return ResourceCreatedData.from(fields);
            case "resource.taken":
                return ResourceTakenData.from(fields);
            case "resource.confirmed":
                return ResourceConfirmedData.from(fields);
            case "resource.released":
                return ResourceReleasedData.from(fields);
            case "resource.extended":
                return ResourceExtendedData.from(fields);
            case "resource.expired":
                return ResourceExpiredData.from(fields);
            case "resource.updated":
                return ResourceUpdatedData.from(fields);
            case "resource.deleted":
                return ResourceDeletedData.from(fields);
            case "resource.queued":
                return ResourceQueuedData.from(fields);
            case "resource.take_failed":
                return ResourceTakeFailedData.from(fields);
            case "lock.acquired":
                return LockAcquiredData.from(fields);
            case "lock.released":
                return LockReleasedData.from(fields);
            case "lock.acquire_failed":
                return LockAcquireFailedData.from(fields);
            case "lock.deadlock_detected":
                return DeadlockDetectedData.from(fields);
            case "lock.abandoned":
                return LockAbandonedData.from(fields);
            case "transaction.started":
                return TransactionStartedData.from(fields);
            case "transaction.completed":
                return TransactionCompletedData.from(fields);
            case "transaction.aborted":
                return TransactionAbortedData.from(fields);
            case "transaction.renewed":
                return TransactionRenewedData.from(fields);
            case "transaction.expired":
                return TransactionExpiredData.from(fields);
            default:
                return UnknownEventData.from(fields);
        }
    }
}
