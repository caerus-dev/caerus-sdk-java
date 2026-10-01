package dev.caerus.sdk.dls;

import dev.caerus.sdk.AbortController;
import dev.caerus.sdk.AbortSignal;
import dev.caerus.sdk.internal.Futures;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

final class TransactionScope implements TransactionContext {

    static final long DEFAULT_LIFETIME_MS = 10_000L;
    static final long MIN_RENEW_INTERVAL_MS = 1_000L;

    private final DlsOperations operations;
    private final String transactionId;
    private final TransactionOptions options;
    private final AbortController lostController = new AbortController();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final Object heartbeatLock = new Object();

    private volatile boolean closed;
    private volatile DlsError lost;
    private ScheduledFuture<?> heartbeat;

    private TransactionScope(DlsOperations operations, String transactionId, TransactionOptions options) {
        this.operations = operations;
        this.transactionId = transactionId;
        this.options = options;
    }

    static <T, E extends Exception> T run(
            DlsOperations operations, TransactionCallback<T, E> callback, TransactionOptions options) throws E {
        TransactionOptions resolved = options == null ? TransactionOptions.none() : options;

        BeginTransactionOptions.Builder begin = BeginTransactionOptions.builder();
        resolved.timeoutMs().ifPresent(begin::timeoutMs);
        Transaction transaction = await(operations.beginTransactionAsync(begin.build()));

        TransactionScope scope = new TransactionScope(operations, transaction.transactionId(), resolved);
        if (resolved.autoRenew().orElse(true)) {
            scope.startHeartbeat();
        }

        try {
            T result = callback.run(scope);
            DlsError lostError = scope.lost;
            if (lostError != null) {
                throw lostError;
            }
            return result;
        } finally {
            scope.close();
            try {
                await(operations.releaseTransactionLocksAsync(scope.transactionId));
            } catch (RuntimeException ignored) {
            }
        }
    }

    static long lifetimeMs(TransactionOptions options) {
        long timeout = options.timeoutMs().orElse(0L);
        return timeout > 0 ? timeout : DEFAULT_LIFETIME_MS;
    }

    static long renewIntervalMs(long lifetimeMs) {
        return Math.max(MIN_RENEW_INTERVAL_MS, lifetimeMs / 2);
    }

    @Override
    public String transactionId() {
        return transactionId;
    }

    @Override
    public AbortSignal signal() {
        return lostController.signal();
    }

    @Override
    public LockHolder acquireLock(String namespace, String lockKey, LockMode mode, AcquireLockOptions acquireOptions) {
        if (closed) {
            throw new IllegalStateException("Transaction context already closed");
        }
        DlsError lostError = lost;
        if (lostError != null) {
            throw lostError;
        }
        AcquireLockOptions given = acquireOptions == null ? AcquireLockOptions.none() : acquireOptions;
        AbortSignal signal = given.signal()
                .or(options::signal)
                .orElse(lostController.signal());

        AcquireLockOptions.Builder merged = AcquireLockOptions.builder().signal(signal);
        given.idempotencyKey().ifPresent(merged::idempotencyKey);
        given.timeoutMs().ifPresent(merged::timeoutMs);
        given.onQueued().ifPresent(merged::onQueued);

        return await(operations.acquireLockAsync(namespace, lockKey, transactionId, mode, merged.build()));
    }

    @Override
    public Transaction renewTransaction(long extraMs) {
        if (closed) {
            throw new IllegalStateException("Transaction context already closed");
        }
        return await(operations.renewTransactionAsync(transactionId, extraMs));
    }

    @Override
    public String toString() {
        return "TransactionContext[" + transactionId + "]";
    }

    private void startHeartbeat() {
        long lifetime = lifetimeMs(options);
        long interval = renewIntervalMs(lifetime);
        synchronized (heartbeatLock) {
            heartbeat = operations.renewalScheduler().scheduleAtFixedRate(
                    () -> renew(lifetime), interval, interval, TimeUnit.MILLISECONDS);
        }
    }

    private void renew(long lifetime) {
        if (closed || lost != null) {
            return;
        }
        CompletableFuture<Transaction> renewal;
        try {
            renewal = operations.renewTransactionAsync(transactionId, lifetime);
        } catch (RuntimeException e) {
            renewal = CompletableFuture.failedFuture(e);
        }
        renewal.whenComplete((ignored, error) -> {
            if (error == null) {
                consecutiveFailures.set(0);
                return;
            }
            Throwable cause = Futures.unwrap(error);
            int failures = consecutiveFailures.incrementAndGet();
            if (DlsErrors.isGone(cause) || failures >= 2) {
                giveUp(cause);
            }
        });
    }

    private void giveUp(Throwable error) {
        if (closed) {
            return;
        }
        DlsError lostError;
        synchronized (heartbeatLock) {
            if (lost != null) {
                return;
            }
            lostError = DlsErrors.toDlsError(error);
            lost = lostError;
            stopHeartbeat();
        }
        try {
            lostController.abort(lostError);
        } catch (RuntimeException e) {
            operations.logger().error("signal listener threw", e);
        }
        Consumer<DlsError> onLost = options.onTransactionLost().orElse(null);
        if (onLost != null) {
            try {
                onLost.accept(lostError);
            } catch (RuntimeException e) {
                operations.logger().error("onTransactionLost callback threw", e);
            }
        }
    }

    private void close() {
        closed = true;
        synchronized (heartbeatLock) {
            stopHeartbeat();
        }
    }

    private void stopHeartbeat() {
        if (heartbeat != null) {
            heartbeat.cancel(false);
            heartbeat = null;
        }
    }

    private static <T> T await(CompletableFuture<T> future) {
        return Futures.await(future, DlsErrors::interrupted, DlsErrors::unexpected);
    }
}
