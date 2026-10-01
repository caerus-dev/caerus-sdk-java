package dev.caerus.sdk.dls;

import dev.caerus.sdk.internal.ClientProfile;
import dev.caerus.sdk.internal.ClientSettings;
import dev.caerus.sdk.internal.Futures;

import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

public final class DlsClient implements DlsApi {

    private final DlsCore core;
    private final String endpoint;

    public DlsClient(DlsClientOptions options) {
        this(resolve(options, ClientSettings.systemEnvironment()));
    }

    DlsClient(ClientSettings settings) {
        this.endpoint = settings.endpoint();
        this.core = new DlsCore(settings);
    }

    static ClientSettings resolve(DlsClientOptions options, UnaryOperator<String> environment) {
        if (options == null) {
            throw new IllegalArgumentException("DlsClient requires an options object with an apiKey");
        }
        return ClientSettings.resolve(
                ClientProfile.DLS,
                options.apiKey(),
                options.endpoint().orElse(null),
                options.tls().orElse(null),
                options.timeoutMs().orElse(null),
                options.logger().orElse(null),
                environment);
    }

    @Override
    public <T, E extends Exception> T withTransaction(TransactionCallback<T, E> callback, TransactionOptions options)
            throws E {
        return TransactionScope.run(core, callback, options);
    }

    @Override
    public Transaction beginTransaction(BeginTransactionOptions options) {
        return await(core.beginTransactionAsync(options));
    }

    @Override
    public LockHolder acquireLock(
            String namespace, String lockKey, String transactionId, LockMode mode, AcquireLockOptions options) {
        return await(core.acquireLockAsync(namespace, lockKey, transactionId, mode, options));
    }

    @Override
    public Transaction renewTransaction(String transactionId, long extraMs) {
        return await(core.renewTransactionAsync(transactionId, extraMs));
    }

    @Override
    public void releaseLock(String lockId, String transactionId) {
        await(core.releaseLockAsync(lockId, transactionId));
    }

    @Override
    public void releaseTransactionLocks(String transactionId) {
        await(core.releaseTransactionLocksAsync(transactionId));
    }

    @Override
    public LockStatusResponse getLockStatus(String namespace, String lockKey) {
        return await(core.getLockStatusAsync(namespace, lockKey));
    }

    @Override
    public TransactionStatusResponse getTransactionStatus(String transactionId) {
        return await(core.getTransactionStatusAsync(transactionId));
    }

    @Override
    public void close() {
        core.close();
    }

    @Override
    public String toString() {
        return "DlsClient[endpoint=" + endpoint + "]";
    }

    DlsCore core() {
        return core;
    }

    private static <T> T await(CompletableFuture<T> future) {
        return Futures.await(future, DlsErrors::interrupted, DlsErrors::unexpected);
    }
}
