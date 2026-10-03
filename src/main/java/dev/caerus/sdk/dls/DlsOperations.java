package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusLogger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

interface DlsOperations {

    CompletableFuture<Transaction> beginTransactionAsync(BeginTransactionOptions options);

    CompletableFuture<LockHolder> acquireLockAsync(
            String namespace, String lockKey, String transactionId, LockMode mode, AcquireLockOptions options);

    CompletableFuture<Transaction> renewTransactionAsync(String transactionId, long extraMs);

    CompletableFuture<Void> releaseTransactionLocksAsync(String transactionId);

    ScheduledExecutorService renewalScheduler();

    CaerusLogger logger();
}
