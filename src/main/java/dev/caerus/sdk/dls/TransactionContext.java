package dev.caerus.sdk.dls;

import dev.caerus.sdk.AbortSignal;

public interface TransactionContext {

    String transactionId();

    AbortSignal signal();

    default LockHolder acquireLock(String namespace, String lockKey, LockMode mode) {
        return acquireLock(namespace, lockKey, mode, AcquireLockOptions.none());
    }

    LockHolder acquireLock(String namespace, String lockKey, LockMode mode, AcquireLockOptions options);

    Transaction renewTransaction(long extraMs);
}
