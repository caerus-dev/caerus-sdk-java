package dev.caerus.sdk.webhooks;

import java.util.Optional;

public sealed interface EventData permits
        ResourceCreatedData,
        ResourceTakenData,
        ResourceConfirmedData,
        ResourceReleasedData,
        ResourceExtendedData,
        ResourceExpiredData,
        ResourceUpdatedData,
        ResourceDeletedData,
        ResourceQueuedData,
        ResourceTakeFailedData,
        LockAcquiredData,
        LockReleasedData,
        LockAcquireFailedData,
        DeadlockDetectedData,
        LockAbandonedData,
        TransactionStartedData,
        TransactionCompletedData,
        TransactionAbortedData,
        TransactionRenewedData,
        TransactionExpiredData,
        UnknownEventData {

    String eventId();

    String occurredOn();

    Optional<String> environmentId();

    Optional<String> apiKeyId();

    Optional<String> actorId();
}
