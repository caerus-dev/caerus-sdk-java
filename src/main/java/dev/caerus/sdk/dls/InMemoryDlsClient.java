package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.CaerusLogger;
import dev.caerus.sdk.ErrorCode;
import dev.caerus.sdk.ErrorReason;
import dev.caerus.sdk.internal.ClientProfile;
import dev.caerus.sdk.internal.StderrLogger;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class InMemoryDlsClient implements DlsApi {

    private static final long DEFAULT_TTL_MS = 10_000L;

    private final Object lock = new Object();
    private final Clock clock;
    private final CaerusLogger logger = new StderrLogger(ClientProfile.DLS.loggerPrefix());
    private final Set<String> activeTransactions = new HashSet<>();
    private final Map<String, List<MockLock>> locks = new LinkedHashMap<>();
    private final Map<String, Long> txExpiration = new HashMap<>();
    private final Operations operations = new Operations();

    private long nextLockId = 1;
    private long nextFencingToken = 1000;
    private boolean closed;
    private ScheduledThreadPoolExecutor scheduler;

    public InMemoryDlsClient() {
        this(Clock.systemUTC());
    }

    InMemoryDlsClient(Clock clock) {
        this.clock = clock;
    }

    @Override
    public <T, E extends Exception> T withTransaction(TransactionCallback<T, E> callback, TransactionOptions options)
            throws E {
        return TransactionScope.run(operations, callback, options);
    }

    @Override
    public Transaction beginTransaction(BeginTransactionOptions options) {
        synchronized (lock) {
            guard();
            BeginTransactionOptions resolved = options == null ? BeginTransactionOptions.none() : options;
            String transactionId = "tx-mock-" + clock.millis() + "-" + randomSuffix();
            activeTransactions.add(transactionId);
            long timeout = resolved.timeoutMs().orElse(0L);
            txExpiration.put(transactionId, clock.millis() + (timeout > 0 ? timeout : DEFAULT_TTL_MS));
            return new Transaction(transactionId);
        }
    }

    @Override
    public LockHolder acquireLock(
            String namespace, String lockKey, String transactionId, LockMode mode, AcquireLockOptions options) {
        synchronized (lock) {
            guard();
            AcquireLockOptions resolved = options == null ? AcquireLockOptions.none() : options;
            if (resolved.signal().map(signal -> signal.aborted()).orElse(false)) {
                throw DlsErrors.abortedByUser(null);
            }

            if (!activeTransactions.contains(transactionId)) {
                throw new DlsNotFoundError("Transaction not found or expired");
            }

            String key = namespace + ":" + lockKey;
            long now = clock.millis();
            List<MockLock> active = locks.getOrDefault(key, new ArrayList<>()).stream()
                    .filter(held -> held.expiresAt > now)
                    .collect(Collectors.toCollection(ArrayList::new));

            if (!active.isEmpty()
                    && (mode == LockMode.EXCLUSIVE || active.stream().anyMatch(held -> held.mode == LockMode.EXCLUSIVE))) {
                throw new LockDeniedError(
                        "Caerus denied the lock on " + namespace + "/" + lockKey
                                + ": it is already held by another transaction.",
                        CaerusErrorOptions.builder().reason(ErrorReason.LOCK_DENIED).build());
            }

            MockLock created = new MockLock();
            created.lockId = "lock-mock-" + nextLockId++;
            created.transactionId = transactionId;
            created.namespace = namespace;
            created.lockKey = lockKey;
            created.mode = mode;
            created.fencingToken = nextFencingToken++;
            created.expiresAt = txExpiration.getOrDefault(transactionId, now + DEFAULT_TTL_MS);

            active.add(created);
            locks.put(key, active);

            return new LockHolder(created.lockId, OptionalLong.of(created.fencingToken), LockStatus.ACQUIRED);
        }
    }

    @Override
    public Transaction renewTransaction(String transactionId, long extraMs) {
        synchronized (lock) {
            guard();
            if (!activeTransactions.contains(transactionId)) {
                throw new DlsNotFoundError("Transaction not found");
            }
            long current = txExpiration.getOrDefault(transactionId, clock.millis());
            txExpiration.put(transactionId, current + extraMs);
            for (List<MockLock> held : locks.values()) {
                for (MockLock mockLock : held) {
                    if (mockLock.transactionId.equals(transactionId)) {
                        mockLock.expiresAt += extraMs;
                    }
                }
            }
            return new Transaction(transactionId);
        }
    }

    @Override
    public void releaseLock(String lockId, String transactionId) {
        synchronized (lock) {
            guard();
            for (List<MockLock> held : locks.values()) {
                if (held.removeIf(mockLock -> mockLock.lockId.equals(lockId)
                        && mockLock.transactionId.equals(transactionId))) {
                    return;
                }
            }
        }
    }

    @Override
    public void releaseTransactionLocks(String transactionId) {
        synchronized (lock) {
            guard();
            for (List<MockLock> held : locks.values()) {
                held.removeIf(mockLock -> mockLock.transactionId.equals(transactionId));
            }
            activeTransactions.remove(transactionId);
            txExpiration.remove(transactionId);
        }
    }

    @Override
    public LockStatusResponse getLockStatus(String namespace, String lockKey) {
        synchronized (lock) {
            guard();
            long now = clock.millis();
            List<MockLock> active = locks.getOrDefault(namespace + ":" + lockKey, List.of()).stream()
                    .filter(held -> held.expiresAt > now)
                    .collect(Collectors.toList());
            return new LockStatusResponse(
                    !active.isEmpty(),
                    active.isEmpty() ? Optional.empty() : Optional.of(active.get(0).mode),
                    active.stream()
                            .map(held -> new ActiveLockHolder(held.lockId, held.expiresAt,
                                    OptionalLong.of(held.fencingToken)))
                            .collect(Collectors.toList()),
                    0);
        }
    }

    @Override
    public TransactionStatusResponse getTransactionStatus(String transactionId) {
        synchronized (lock) {
            guard();
            if (!activeTransactions.contains(transactionId)) {
                throw new DlsNotFoundError("Transaction not found");
            }
            List<TransactionLockInfo> owned = new ArrayList<>();
            for (List<MockLock> held : locks.values()) {
                for (MockLock mockLock : held) {
                    if (mockLock.transactionId.equals(transactionId)) {
                        owned.add(new TransactionLockInfo(
                                mockLock.namespace, mockLock.lockKey, mockLock.mode, LockStatus.ACQUIRED));
                    }
                }
            }
            return new TransactionStatusResponse(
                    "ACTIVE", Optional.empty(), owned, txExpiration.getOrDefault(transactionId, 0L));
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            closed = true;
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
        }
    }

    private void guard() {
        if (closed) {
            throw new DlsError("This InMemoryDlsClient has been closed", ErrorCode.UNKNOWN);
        }
    }

    private static String randomSuffix() {
        return Long.toString(ThreadLocalRandom.current().nextLong(36L * 36 * 36 * 36 * 36 * 36 * 36), 36);
    }

    private static <T> CompletableFuture<T> attempt(Supplier<T> action) {
        try {
            return CompletableFuture.completedFuture(action.get());
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private final class Operations implements DlsOperations {

        @Override
        public CompletableFuture<Transaction> beginTransactionAsync(BeginTransactionOptions options) {
            return attempt(() -> beginTransaction(options));
        }

        @Override
        public CompletableFuture<LockHolder> acquireLockAsync(
                String namespace, String lockKey, String transactionId, LockMode mode, AcquireLockOptions options) {
            return attempt(() -> acquireLock(namespace, lockKey, transactionId, mode, options));
        }

        @Override
        public CompletableFuture<Transaction> renewTransactionAsync(String transactionId, long extraMs) {
            return attempt(() -> renewTransaction(transactionId, extraMs));
        }

        @Override
        public CompletableFuture<Void> releaseTransactionLocksAsync(String transactionId) {
            return attempt(() -> {
                releaseTransactionLocks(transactionId);
                return null;
            });
        }

        @Override
        public ScheduledExecutorService renewalScheduler() {
            synchronized (lock) {
                if (scheduler == null) {
                    scheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
                        Thread thread = new Thread(runnable, "caerus-dls-mock-renewal");
                        thread.setDaemon(true);
                        return thread;
                    });
                    scheduler.setRemoveOnCancelPolicy(true);
                }
                return scheduler;
            }
        }

        @Override
        public CaerusLogger logger() {
            return logger;
        }
    }

    private static final class MockLock {
        private String lockId;
        private String transactionId;
        private String namespace;
        private String lockKey;
        private LockMode mode;
        private long fencingToken;
        private long expiresAt;
    }
}
