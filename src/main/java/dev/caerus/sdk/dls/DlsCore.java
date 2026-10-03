package dev.caerus.sdk.dls;

import dev.caerus.sdk.AbortSignal;
import dev.caerus.sdk.CaerusLogger;
import dev.caerus.sdk.internal.ClientSettings;
import dev.caerus.sdk.internal.GrpcTransport;
import dev.caerus.sdk.internal.proto.dls.AcquireLockRequest;
import dev.caerus.sdk.internal.proto.dls.AcquireLockResponse;
import dev.caerus.sdk.internal.proto.dls.BeginTransactionRequest;
import dev.caerus.sdk.internal.proto.dls.DistributedLockingEngineGrpc;
import dev.caerus.sdk.internal.proto.dls.GetLockStatusRequest;
import dev.caerus.sdk.internal.proto.dls.GetTransactionStatusRequest;
import dev.caerus.sdk.internal.proto.dls.ReleaseLockRequest;
import dev.caerus.sdk.internal.proto.dls.ReleaseTransactionLocksRequest;
import dev.caerus.sdk.internal.proto.dls.RenewTransactionRequest;
import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

final class DlsCore implements DlsOperations {

    private final GrpcTransport transport;
    private final CaerusLogger logger;
    private final Object schedulerLock = new Object();
    private ScheduledThreadPoolExecutor scheduler;

    DlsCore(ClientSettings settings) {
        this.transport = new GrpcTransport(settings);
        this.logger = settings.logger();
    }

    @Override
    public CompletableFuture<Transaction> beginTransactionAsync(BeginTransactionOptions options) {
        BeginTransactionOptions resolved = options == null ? BeginTransactionOptions.none() : options;
        BeginTransactionRequest.Builder request = BeginTransactionRequest.newBuilder();
        resolved.timeoutMs().ifPresent(request::setTimeoutMs);
        return call(DistributedLockingEngineGrpc.getBeginTransactionMethod(), request.build())
                .thenApply(response -> new Transaction(response.getTransactionId()));
    }

    @Override
    public CompletableFuture<LockHolder> acquireLockAsync(
            String namespace, String lockKey, String transactionId, LockMode mode, AcquireLockOptions options) {
        AcquireLockOptions resolved = options == null ? AcquireLockOptions.none() : options;

        AcquireLockRequest.Builder request = AcquireLockRequest.newBuilder()
                .setNamespace(DlsMapping.text(namespace))
                .setLockKey(DlsMapping.text(lockKey))
                .setRequestedMode(DlsMapping.toGrpc(mode))
                .setTransactionId(DlsMapping.text(transactionId));
        resolved.idempotencyKey().ifPresent(request::setIdempotencyKey);

        return acquireLockStream(
                request.build(),
                resolved.timeoutMs().orElse(null),
                resolved.signal().orElse(null),
                resolved.onQueued().orElse(null))
                .thenApply(response -> DlsMapping.assertAcquired(
                        new LockHolder(
                                response.getLockId(),
                                DlsMapping.decodeFencingToken(response.getFencingToken()),
                                DlsMapping.toLockStatus(response.getStatusValue())),
                        namespace,
                        lockKey));
    }

    @Override
    public CompletableFuture<Transaction> renewTransactionAsync(String transactionId, long extraMs) {
        RenewTransactionRequest request = RenewTransactionRequest.newBuilder()
                .setTransactionId(DlsMapping.text(transactionId))
                .setExtraMs(extraMs)
                .build();
        return call(DistributedLockingEngineGrpc.getRenewTransactionMethod(), request)
                .thenApply(response -> new Transaction(response.getTransactionId()));
    }

    CompletableFuture<Void> releaseLockAsync(String lockId, String transactionId) {
        ReleaseLockRequest request = ReleaseLockRequest.newBuilder()
                .setLockId(DlsMapping.text(lockId))
                .setTransactionId(DlsMapping.text(transactionId))
                .build();
        return call(DistributedLockingEngineGrpc.getReleaseLockMethod(), request).thenApply(ignored -> null);
    }

    @Override
    public CompletableFuture<Void> releaseTransactionLocksAsync(String transactionId) {
        ReleaseTransactionLocksRequest request = ReleaseTransactionLocksRequest.newBuilder()
                .setTransactionId(DlsMapping.text(transactionId))
                .build();
        return call(DistributedLockingEngineGrpc.getReleaseTransactionLocksMethod(), request)
                .thenApply(ignored -> null);
    }

    CompletableFuture<LockStatusResponse> getLockStatusAsync(String namespace, String lockKey) {
        GetLockStatusRequest request = GetLockStatusRequest.newBuilder()
                .setNamespace(DlsMapping.text(namespace))
                .setLockKey(DlsMapping.text(lockKey))
                .build();
        return call(DistributedLockingEngineGrpc.getGetLockStatusMethod(), request)
                .thenApply(response -> {
                    List<ActiveLockHolder> holders = response.getActiveHoldersList().stream()
                            .map(holder -> new ActiveLockHolder(
                                    holder.getLockId(),
                                    holder.getExpiresAt(),
                                    DlsMapping.decodeFencingToken(holder.getFencingToken())))
                            .collect(Collectors.toList());
                    return new LockStatusResponse(
                            response.getIsHeld(),
                            DlsMapping.toLockMode(response.getCurrentModeValue()),
                            holders,
                            response.getPendingQueueSize());
                });
    }

    CompletableFuture<TransactionStatusResponse> getTransactionStatusAsync(String transactionId) {
        GetTransactionStatusRequest request = GetTransactionStatusRequest.newBuilder()
                .setTransactionId(DlsMapping.text(transactionId))
                .build();
        return call(DistributedLockingEngineGrpc.getGetTransactionStatusMethod(), request)
                .thenApply(response -> {
                    List<TransactionLockInfo> locks = response.getLocksList().stream()
                            .map(lock -> new TransactionLockInfo(
                                    lock.getNamespace(),
                                    lock.getLockKey(),
                                    DlsMapping.toLockMode(lock.getRequestedModeValue()).orElse(LockMode.EXCLUSIVE),
                                    DlsMapping.toLockStatus(lock.getStatusValue())))
                            .collect(Collectors.toList());
                    Optional<String> abortReason = response.getAbortReason().isEmpty()
                            ? Optional.empty()
                            : Optional.of(response.getAbortReason());
                    return new TransactionStatusResponse(
                            response.getStatus(), abortReason, locks, response.getExpiresAt());
                });
    }

    @Override
    public ScheduledExecutorService renewalScheduler() {
        synchronized (schedulerLock) {
            if (scheduler == null) {
                scheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
                    Thread thread = new Thread(runnable, "caerus-dls-renewal");
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

    void close() {
        transport.close();
        synchronized (schedulerLock) {
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
        }
    }

    boolean schedulerShutDown() {
        synchronized (schedulerLock) {
            return scheduler == null || scheduler.isShutdown();
        }
    }

    private CompletableFuture<AcquireLockResponse> acquireLockStream(
            AcquireLockRequest request, Long timeoutMs, AbortSignal signal, Runnable onQueued) {
        if (transport.isClosed()) {
            return CompletableFuture.failedFuture(DlsErrors.closed());
        }
        if (signal != null && signal.aborted()) {
            return CompletableFuture.failedFuture(DlsErrors.abortedByUser(null));
        }

        String requestId = GrpcTransport.newRequestId();
        long deadline = timeoutMs != null && timeoutMs > 0 ? timeoutMs : transport.timeoutMs();
        ClientCall<AcquireLockRequest, AcquireLockResponse> call = transport.newCall(
                DistributedLockingEngineGrpc.getAcquireLockMethod(), requestId, deadline);

        CompletableFuture<AcquireLockResponse> result = new CompletableFuture<>();
        AtomicBoolean terminal = new AtomicBoolean();
        AtomicBoolean queuedNotified = new AtomicBoolean();
        AtomicReference<Runnable> stopListening = new AtomicReference<>(() -> {
        });

        ClientCalls.asyncServerStreamingCall(call, request, new StreamObserver<>() {
            @Override
            public void onNext(AcquireLockResponse response) {
                int status = response.getStatusValue();
                if (status == dev.caerus.sdk.internal.proto.dls.LockStatus.ACQUIRED_VALUE
                        || status == dev.caerus.sdk.internal.proto.dls.LockStatus.DENIED_VALUE) {
                    if (terminal.compareAndSet(false, true)) {
                        stopListening.get().run();
                        result.complete(response);
                        call.cancel("Terminal lock status received", null);
                    }
                } else if (status == dev.caerus.sdk.internal.proto.dls.LockStatus.QUEUED_VALUE) {
                    if (!terminal.get() && queuedNotified.compareAndSet(false, true) && onQueued != null) {
                        try {
                            onQueued.run();
                        } catch (RuntimeException e) {
                            logger.error("onQueued callback threw", e);
                        }
                    }
                } else {
                    logger.error("Received unspecified lock status in stream");
                }
            }

            @Override
            public void onError(Throwable error) {
                if (terminal.compareAndSet(false, true)) {
                    stopListening.get().run();
                    result.completeExceptionally(DlsErrors.toDlsError(error, requestId));
                }
            }

            @Override
            public void onCompleted() {
                if (terminal.compareAndSet(false, true)) {
                    stopListening.get().run();
                    result.completeExceptionally(DlsErrors.toDlsError(
                            new IllegalStateException("Stream ended without terminal status"), requestId));
                }
            }
        });

        if (signal != null) {
            stopListening.set(signal.onAbort(() -> {
                if (terminal.compareAndSet(false, true)) {
                    call.cancel(DlsErrors.ABORTED_BY_USER, null);
                    result.completeExceptionally(DlsErrors.abortedByUser(requestId));
                }
            }));
            if (terminal.get()) {
                stopListening.get().run();
            }
        }

        result.whenComplete((ignored, error) -> {
            if (result.isCancelled() && terminal.compareAndSet(false, true)) {
                stopListening.get().run();
                call.cancel("Cancelled by the caller", null);
            }
        });
        return result;
    }

    private <Req, Res> CompletableFuture<Res> call(MethodDescriptor<Req, Res> method, Req request) {
        return transport.unary(method, request, DlsErrors::toDlsError, DlsErrors::closed);
    }
}
