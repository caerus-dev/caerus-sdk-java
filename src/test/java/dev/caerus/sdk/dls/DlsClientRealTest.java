package dev.caerus.sdk.dls;

import dev.caerus.sdk.AbortController;
import dev.caerus.sdk.ErrorCode;
import dev.caerus.sdk.internal.proto.dls.AcquireLockRequest;
import dev.caerus.sdk.internal.proto.dls.AcquireLockResponse;
import dev.caerus.sdk.internal.proto.dls.BeginTransactionRequest;
import dev.caerus.sdk.internal.proto.dls.BeginTransactionResponse;
import dev.caerus.sdk.internal.proto.dls.LockStatus;
import dev.caerus.sdk.internal.proto.dls.ReleaseTransactionLocksRequest;
import dev.caerus.sdk.internal.proto.dls.RenewTransactionRequest;
import dev.caerus.sdk.internal.proto.dls.RenewTransactionResponse;
import dev.caerus.sdk.support.FakeDlsEngine;
import dev.caerus.sdk.support.GrpcErrors;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static dev.caerus.sdk.support.FakeDlsEngine.acquired;
import static dev.caerus.sdk.support.FakeDlsEngine.withStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class DlsClientRealTest {

    private static FakeDlsEngine engine;
    private static DlsClient client;
    private static ScheduledExecutorService later;
    private final List<String> calls = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void start() {
        engine = FakeDlsEngine.start();
        client = DlsTestClients.real(engine);
        later = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterAll
    static void stop() {
        later.shutdownNow();
        client.close();
        engine.close();
    }

    @BeforeEach
    void script() {
        engine.reset();
        calls.clear();
        engine.<BeginTransactionRequest, BeginTransactionResponse>on("beginTransaction", (request, observer) -> {
            calls.add("begin");
            reply(observer, BeginTransactionResponse.newBuilder().setTransactionId("tx-1").build());
        });
        engine.<ReleaseTransactionLocksRequest, Empty>on("releaseTransactionLocks", (request, observer) -> {
            calls.add("releaseAll");
            reply(observer, Empty.getDefaultInstance());
        });
        engine.<RenewTransactionRequest, RenewTransactionResponse>on("renewTransaction", (request, observer) -> {
            calls.add("renew");
            reply(observer, RenewTransactionResponse.newBuilder().setTransactionId("tx-1").build());
        });
        engine.<AcquireLockRequest, AcquireLockResponse>on("acquireLock", (request, observer) ->
                reply(observer, acquired("lock-1", 7)));
    }

    private static <T> void reply(StreamObserver<T> observer, T response) {
        observer.onNext(response);
        observer.onCompleted();
    }

    private static TransactionOptions noRenew() {
        return TransactionOptions.builder().autoRenew(false).build();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    void opensTheTransactionRunsTheCallbackAndReleasesTheLocksAtTheEnd() {
        String result = client.withTransaction(tx -> {
            LockHolder lock = tx.acquireLock("ns", "k", LockMode.EXCLUSIVE);
            return tx.transactionId() + ":" + lock.status() + ":" + lock.fencingToken().getAsLong();
        }, noRenew());

        assertThat(result).isEqualTo("tx-1:ACQUIRED:7");
        assertThat(calls).containsExactly("begin", "releaseAll");
        assertThat(engine.lastRequest(ReleaseTransactionLocksRequest.class).getTransactionId()).isEqualTo("tx-1");
    }

    @Test
    void releasesTheLocksEvenWhenTheCallbackBlowsUpAndRethrows() {
        assertThatThrownBy(() -> client.withTransaction(tx -> {
            throw new IllegalStateException("se rompio el trabajo");
        }, noRenew())).isInstanceOf(IllegalStateException.class).hasMessage("se rompio el trabajo");

        assertThat(calls).contains("releaseAll");
    }

    @Test
    void letsACheckedExceptionThroughUnwrapped() {
        Throwable error = catchThrowable(() -> client.withTransaction(tx -> {
            throw new java.io.IOException("disco lleno");
        }, noRenew()));

        assertThat(error).isInstanceOf(java.io.IOException.class).hasMessage("disco lleno");
        assertThat(calls).contains("releaseAll");
    }

    @Test
    void aDeniedLockInsideTheTransactionCutsItAndStillReleases() {
        engine.<AcquireLockRequest, AcquireLockResponse>on("acquireLock", (request, observer) ->
                reply(observer, withStatus(LockStatus.DENIED)));

        assertThatThrownBy(() -> client.withTransaction(tx -> tx.acquireLock("ns", "k", LockMode.EXCLUSIVE), noRenew()))
                .isInstanceOf(LockDeniedError.class);

        assertThat(calls).contains("releaseAll");
    }

    @Test
    void aFailedReleaseAtTheEndIsSwallowed() {
        engine.on("releaseTransactionLocks", (request, observer) ->
                observer.onError(GrpcErrors.status(Status.Code.UNAVAILABLE, "se cayo")));

        String result = client.withTransaction(tx -> "listo", noRenew());

        assertThat(result).isEqualTo("listo");
    }

    @Test
    void renewsOnItsOwnWhileTheWorkLasts() {
        client.withTransaction(tx -> {
            sleep(2300);
            return null;
        }, TransactionOptions.builder().timeoutMs(2000L).build());

        assertThat(calls.stream().filter("renew"::equals).count()).isGreaterThanOrEqualTo(2);
        assertThat(calls).contains("releaseAll");
    }

    @Test
    void renewsWithTheWholeLifetime() {
        AtomicReference<RenewTransactionRequest> seen = new AtomicReference<>();
        engine.<RenewTransactionRequest, RenewTransactionResponse>on("renewTransaction", (request, observer) -> {
            seen.set(request);
            reply(observer, RenewTransactionResponse.newBuilder().setTransactionId("tx-1").build());
        });

        client.withTransaction(tx -> {
            sleep(1300);
            return null;
        }, TransactionOptions.builder().timeoutMs(2000L).build());

        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().getTransactionId()).isEqualTo("tx-1");
        assertThat(seen.get().getExtraMs()).isEqualTo(2000);
    }

    @Test
    void withAutoRenewOffItSendsNotASingleHeartbeat() {
        client.withTransaction(tx -> {
            sleep(1600);
            return null;
        }, TransactionOptions.builder().timeoutMs(2000L).autoRenew(false).build());

        assertThat(calls).doesNotContain("renew");
    }

    @Test
    void stopsRenewingOnceTheCallbackEnds() {
        client.withTransaction(tx -> {
            sleep(1200);
            return null;
        }, TransactionOptions.builder().timeoutMs(2000L).build());
        long renewalsAtTheEnd = calls.stream().filter("renew"::equals).count();

        sleep(1500);

        assertThat(calls.stream().filter("renew"::equals).count()).isEqualTo(renewalsAtTheEnd);
    }

    @Test
    void ifTheRenewalFailsBeyondRepairTheTransactionIsLost() {
        engine.on("renewTransaction", (request, observer) -> {
            calls.add("renew");
            observer.onError(GrpcErrors.status(Status.Code.NOT_FOUND, "la transaccion ya no existe"));
        });
        AtomicReference<DlsError> notified = new AtomicReference<>();
        AtomicBoolean aborted = new AtomicBoolean();

        Throwable error = catchThrowable(() -> client.withTransaction(tx -> {
            tx.signal().onAbort(() -> aborted.set(true));
            sleep(1600);
            return "el callback termino igual";
        }, TransactionOptions.builder().timeoutMs(2000L).onTransactionLost(notified::set).build()));

        assertThat(error).isInstanceOf(DlsNotFoundError.class);
        assertThat(notified.get()).isInstanceOf(DlsNotFoundError.class).isSameAs(error);
        assertThat(aborted).isTrue();
        assertThat(calls).contains("releaseAll");
    }

    @Test
    void twoFailuresInARowOfAnyKindAlsoLoseIt() {
        engine.on("renewTransaction", (request, observer) -> {
            calls.add("renew");
            observer.onError(GrpcErrors.status(Status.Code.UNAVAILABLE, "se corto"));
        });

        Throwable error = catchThrowable(() -> client.withTransaction(tx -> {
            sleep(2600);
            return "nada";
        }, TransactionOptions.builder().timeoutMs(2000L).build()));

        assertThat(error).isInstanceOf(DlsError.class);
        assertThat(((DlsError) error).code()).isEqualTo(ErrorCode.UNKNOWN);
        assertThat(calls.stream().filter("renew"::equals).count()).isEqualTo(2);
    }

    @Test
    void oneFailureFollowedByASuccessIsForgiven() {
        AtomicBoolean failedOnce = new AtomicBoolean();
        engine.<RenewTransactionRequest, RenewTransactionResponse>on("renewTransaction", (request, observer) -> {
            calls.add("renew");
            if (failedOnce.compareAndSet(false, true)) {
                observer.onError(GrpcErrors.status(Status.Code.UNAVAILABLE, "se corto"));
                return;
            }
            reply(observer, RenewTransactionResponse.newBuilder().setTransactionId("tx-1").build());
        });

        String result = client.withTransaction(tx -> {
            sleep(3300);
            return "sobrevivio";
        }, TransactionOptions.builder().timeoutMs(2000L).build());

        assertThat(result).isEqualTo("sobrevivio");
        assertThat(calls.stream().filter("renew"::equals).count()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void onceLostItWillNotTakeMoreLocks() {
        engine.on("renewTransaction", (request, observer) ->
                observer.onError(GrpcErrors.status(Status.Code.NOT_FOUND, "la transaccion ya no existe")));
        AtomicReference<Throwable> onTake = new AtomicReference<>();

        assertThatThrownBy(() -> client.withTransaction(tx -> {
            sleep(1600);
            onTake.set(catchThrowable(() -> tx.acquireLock("ns", "k", LockMode.EXCLUSIVE)));
            return "no deberia valer";
        }, TransactionOptions.builder().timeoutMs(2000L).build())).isInstanceOf(DlsNotFoundError.class);

        assertThat(onTake.get()).isInstanceOf(DlsNotFoundError.class);
    }

    @Test
    void losingTheTransactionCutsALockThatIsStillWaiting() {
        engine.on("renewTransaction", (request, observer) ->
                observer.onError(GrpcErrors.withReason(Status.Code.FAILED_PRECONDITION, "cerrada", "TRANSACTION_NOT_ACTIVE")));
        engine.<AcquireLockRequest, AcquireLockResponse>on("acquireLock", (request, observer) ->
                observer.onNext(withStatus(LockStatus.QUEUED)));

        long started = System.nanoTime();
        Throwable error = catchThrowable(() -> client.withTransaction(
                tx -> tx.acquireLock("ns", "k", LockMode.EXCLUSIVE, AcquireLockOptions.builder().timeoutMs(8000L).build()),
                TransactionOptions.builder().timeoutMs(2000L).build()));
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(error).isInstanceOf(DlsError.class);
        assertThat(elapsedMs).isLessThan(4000);
    }

    @Test
    void losingTheTransactionAlsoCutsAWaitThatBringsItsOwnSignal() {
        engine.on("renewTransaction", (request, observer) ->
                observer.onError(GrpcErrors.withReason(Status.Code.FAILED_PRECONDITION, "cerrada", "TRANSACTION_NOT_ACTIVE")));
        engine.<AcquireLockRequest, AcquireLockResponse>on("acquireLock", (request, observer) ->
                observer.onNext(withStatus(LockStatus.QUEUED)));
        AbortController own = new AbortController();

        long started = System.nanoTime();
        Throwable error = catchThrowable(() -> client.withTransaction(
                tx -> tx.acquireLock("ns", "k", LockMode.EXCLUSIVE,
                        AcquireLockOptions.builder().signal(own.signal()).timeoutMs(8000L).build()),
                TransactionOptions.builder().timeoutMs(2000L).build()));
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(error).isInstanceOf(DlsError.class);
        assertThat(elapsedMs).isLessThan(4000);
        assertThat(own.signal().aborted()).isFalse();
    }

    @Test
    void itsOwnSignalStillCutsTheWaitInsideATransaction() {
        engine.<AcquireLockRequest, AcquireLockResponse>on("acquireLock", (request, observer) ->
                observer.onNext(withStatus(LockStatus.QUEUED)));
        AbortController own = new AbortController();
        later.schedule(() -> own.abort(), 100, TimeUnit.MILLISECONDS);

        Throwable error = catchThrowable(() -> client.withTransaction(
                tx -> tx.acquireLock("ns", "k", LockMode.EXCLUSIVE,
                        AcquireLockOptions.builder().signal(own.signal()).timeoutMs(8000L).build()),
                noRenew()));

        assertThat(error).isInstanceOf(DlsError.class).hasMessageContaining("AcquireLock aborted by user");
    }

    @Test
    void aSlowRenewalIsNeverOverlappedByTheNextOne() {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        AtomicInteger started = new AtomicInteger();
        engine.<RenewTransactionRequest, RenewTransactionResponse>on("renewTransaction", (request, observer) -> {
            started.incrementAndGet();
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            later.schedule(() -> {
                inFlight.decrementAndGet();
                reply(observer, RenewTransactionResponse.newBuilder().setTransactionId("tx-1").build());
            }, 2500, TimeUnit.MILLISECONDS);
        });

        client.withTransaction(tx -> {
            sleep(3800);
            return null;
        }, TransactionOptions.builder().timeoutMs(2000L).build());

        assertThat(maxInFlight).hasValue(1);
        assertThat(started.get()).isBetween(1, 2);
    }

    @Test
    void onceClosedTheContextCannotBeUsed() {
        AtomicReference<TransactionContext> leaked = new AtomicReference<>();

        client.withTransaction(tx -> {
            leaked.set(tx);
            return null;
        }, noRenew());

        assertThatThrownBy(() -> leaked.get().acquireLock("ns", "k", LockMode.EXCLUSIVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Transaction context already closed");
        assertThatThrownBy(() -> leaked.get().renewTransaction(1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Transaction context already closed");
    }

    @Test
    void theContextRenewsOnRequest() {
        client.withTransaction(tx -> tx.renewTransaction(5000), noRenew());

        assertThat(calls).containsExactly("begin", "renew", "releaseAll");
    }

    @Test
    void waitsInTheQueueAndResolvesWhenTheLockIsGranted() {
        engine.<AcquireLockRequest, AcquireLockResponse>on("acquireLock", (request, observer) -> {
            observer.onNext(withStatus(LockStatus.QUEUED));
            later.schedule(() -> observer.onNext(withStatus(LockStatus.QUEUED)), 60, TimeUnit.MILLISECONDS);
            later.schedule(() -> reply(observer, acquired("lock-9", 42)), 120, TimeUnit.MILLISECONDS);
        });

        LockHolder lock = client.acquireLock("ns", "k", "tx-1", LockMode.EXCLUSIVE,
                AcquireLockOptions.builder().timeoutMs(5000L).build());

        assertThat(lock).isEqualTo(new LockHolder("lock-9", java.util.OptionalLong.of(42), dev.caerus.sdk.dls.LockStatus.ACQUIRED));
    }

    @Test
    void anErrorHalfwayThroughTheStreamArrivesTypedAndWithItsCode() {
        engine.<AcquireLockRequest, AcquireLockResponse>on("acquireLock", (request, observer) -> {
            observer.onNext(withStatus(LockStatus.QUEUED));
            later.schedule(() -> observer.onError(GrpcErrors.status(Status.Code.ABORTED, "la transaccion se aborto")),
                    50, TimeUnit.MILLISECONDS);
        });

        long started = System.nanoTime();
        DlsError error = catchThrowableOfType(DlsError.class, () -> client.acquireLock("ns", "k", "tx-1",
                LockMode.EXCLUSIVE, AcquireLockOptions.builder().timeoutMs(5000L).build()));

        assertThat(error).isInstanceOf(DlsConflictError.class);
        assertThat(error.code()).isNotEqualTo(ErrorCode.TIMEOUT);
        assertThat(error.requestId()).isPresent();
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(2000);
    }

    @Test
    void aStreamThatEndsWithoutATerminalStatusIsNotTakenAsGranted() {
        engine.<AcquireLockRequest, AcquireLockResponse>on("acquireLock", (request, observer) -> {
            observer.onNext(withStatus(LockStatus.QUEUED));
            later.schedule(observer::onCompleted, 50, TimeUnit.MILLISECONDS);
        });

        DlsError error = catchThrowableOfType(DlsError.class, () -> client.acquireLock("ns", "k", "tx-1",
                LockMode.EXCLUSIVE, AcquireLockOptions.builder().timeoutMs(5000L).build()));

        assertThat(error.getMessage()).contains("Stream ended without terminal status");
        assertThat(error.code()).isEqualTo(ErrorCode.UNKNOWN);
    }

    @Test
    void theAbortSignalCutsAWaitInProgress() {
        engine.<AcquireLockRequest, AcquireLockResponse>on("acquireLock", (request, observer) ->
                observer.onNext(withStatus(LockStatus.QUEUED)));
        AbortController controller = new AbortController();
        later.schedule(() -> controller.abort(), 80, TimeUnit.MILLISECONDS);

        long started = System.nanoTime();
        DlsError error = catchThrowableOfType(DlsError.class, () -> client.acquireLock("ns", "k", "tx-1",
                LockMode.EXCLUSIVE, AcquireLockOptions.builder().signal(controller.signal()).timeoutMs(5000L).build()));

        assertThat(error.getMessage()).contains("AcquireLock aborted by user");
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(2000);
    }

    @Test
    void anAlreadyAbortedSignalFailsWithoutCalling() {
        AbortController controller = new AbortController();
        controller.abort();

        DlsError error = catchThrowableOfType(DlsError.class, () -> client.acquireLock("ns", "k", "tx-1",
                LockMode.EXCLUSIVE, AcquireLockOptions.builder().signal(controller.signal()).build()));

        assertThat(error.getMessage()).contains("AcquireLock aborted by user");
        assertThat(engine.lastMethod()).isNull();
    }

    @Test
    void withoutItsOwnTimeoutAQueuedLockUsesTheClientDeadline() {
        engine.<AcquireLockRequest, AcquireLockResponse>on("acquireLock", (request, observer) ->
                observer.onNext(withStatus(LockStatus.QUEUED)));

        try (DlsClient impatient = new DlsClient(DlsClient.resolve(DlsClientOptions.builder()
                .endpoint(engine.endpoint()).apiKey("k").tls(false).timeoutMs(300L).build(), name -> null))) {
            assertThatThrownBy(() -> impatient.acquireLock("ns", "k", "tx-1", LockMode.EXCLUSIVE))
                    .isInstanceOf(DlsTimeoutError.class);
        }
    }

    @Test
    void itsOwnTimeoutBeatsTheClientDeadline() {
        engine.<AcquireLockRequest, AcquireLockResponse>on("acquireLock", (request, observer) -> {
            observer.onNext(withStatus(LockStatus.QUEUED));
            later.schedule(() -> reply(observer, acquired("lock-1", 1)), 600, TimeUnit.MILLISECONDS);
        });

        try (DlsClient impatient = new DlsClient(DlsClient.resolve(DlsClientOptions.builder()
                .endpoint(engine.endpoint()).apiKey("k").tls(false).timeoutMs(300L).build(), name -> null))) {
            LockHolder lock = impatient.acquireLock("ns", "k", "tx-1", LockMode.EXCLUSIVE,
                    AcquireLockOptions.builder().timeoutMs(3000L).build());
            assertThat(lock.lockId()).isEqualTo("lock-1");
        }
    }

    @Test
    void aClosedClientRejectsInsteadOfHanging() {
        DlsClient loose = DlsTestClients.real(engine);
        loose.close();

        assertThatThrownBy(loose::beginTransaction).isInstanceOf(DlsError.class).hasMessageContaining("closed");
        assertThatThrownBy(() -> loose.acquireLock("ns", "k", "tx-1", LockMode.EXCLUSIVE))
                .isInstanceOf(DlsError.class)
                .hasMessageContaining("closed");
    }

    @Test
    void closingShutsTheRenewalThreadDown() {
        DlsClient loose = DlsTestClients.real(engine);
        loose.withTransaction(tx -> {
            sleep(50);
            return null;
        }, TransactionOptions.builder().timeoutMs(2000L).build());
        assertThat(loose.core().schedulerShutDown()).isFalse();

        loose.close();

        assertThat(loose.core().schedulerShutDown()).isTrue();
    }

    @Test
    void severalThreadsCanHoldTransactionsAtOnce() throws InterruptedException {
        List<String> results = new CopyOnWriteArrayList<>();
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            int index = i;
            threads[i] = new Thread(() -> results.add(client.withTransaction(tx -> {
                LockHolder lock = tx.acquireLock("ns", "k" + index, LockMode.EXCLUSIVE);
                return lock.lockId();
            }, noRenew())));
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join(10_000);
        }

        assertThat(results).hasSize(8).containsOnly("lock-1");
        assertThat(calls.stream().filter("releaseAll"::equals).count()).isEqualTo(8);
    }
}
