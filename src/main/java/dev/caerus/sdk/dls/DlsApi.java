package dev.caerus.sdk.dls;

public interface DlsApi extends AutoCloseable {

    default <T, E extends Exception> T withTransaction(TransactionCallback<T, E> callback) throws E {
        return withTransaction(callback, TransactionOptions.none());
    }

    <T, E extends Exception> T withTransaction(TransactionCallback<T, E> callback, TransactionOptions options) throws E;

    default Transaction beginTransaction() {
        return beginTransaction(BeginTransactionOptions.none());
    }

    Transaction beginTransaction(BeginTransactionOptions options);

    default LockHolder acquireLock(String namespace, String lockKey, String transactionId, LockMode mode) {
        return acquireLock(namespace, lockKey, transactionId, mode, AcquireLockOptions.none());
    }

    LockHolder acquireLock(
            String namespace, String lockKey, String transactionId, LockMode mode, AcquireLockOptions options);

    Transaction renewTransaction(String transactionId, long extraMs);

    void releaseLock(String lockId, String transactionId);

    void releaseTransactionLocks(String transactionId);

    LockStatusResponse getLockStatus(String namespace, String lockKey);

    TransactionStatusResponse getTransactionStatus(String transactionId);

    @Override
    void close();
}
