package dev.caerus.sdk.dls;

import com.google.protobuf.Empty;
import dev.caerus.sdk.internal.proto.dls.AcquireLockRequest;
import dev.caerus.sdk.internal.proto.dls.AcquireLockResponse;
import dev.caerus.sdk.internal.proto.dls.BeginTransactionRequest;
import dev.caerus.sdk.internal.proto.dls.BeginTransactionResponse;
import dev.caerus.sdk.internal.proto.dls.LockStatus;
import dev.caerus.sdk.internal.proto.dls.ReleaseTransactionLocksRequest;
import dev.caerus.sdk.support.FakeDlsEngine;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.caerus.sdk.support.FakeDlsEngine.acquired;
import static dev.caerus.sdk.support.FakeDlsEngine.withStatus;
import static org.assertj.core.api.Assertions.assertThat;

class DlsOnQueuedTest {

    private static FakeDlsEngine engine;
    private static DlsClient client;
    private static ScheduledExecutorService later;

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
    void reset() {
        engine.reset();
    }

    private static <T> void reply(StreamObserver<T> observer, T response) {
        observer.onNext(response);
        observer.onCompleted();
    }

    private static void queueThenGrant() {
        engine.<AcquireLockRequest, AcquireLockResponse>on("acquireLock", (request, observer) -> {
            observer.onNext(withStatus(LockStatus.QUEUED));
            later.schedule(() -> observer.onNext(withStatus(LockStatus.QUEUED)), 30, TimeUnit.MILLISECONDS);
            later.schedule(() -> observer.onNext(withStatus(LockStatus.QUEUED)), 60, TimeUnit.MILLISECONDS);
            later.schedule(() -> reply(observer, acquired("lock-1", 5)), 90, TimeUnit.MILLISECONDS);
        });
    }

    private static AcquireLockOptions.Builder patient() {
        return AcquireLockOptions.builder().timeoutMs(5000L);
    }

    @Test
    void notifiesOnceEvenWhenTheEngineSendsSeveralKeepAliveQueued() {
        queueThenGrant();
        AtomicInteger notices = new AtomicInteger();

        LockHolder lock = client.acquireLock("ns", "k", "tx-1", LockMode.EXCLUSIVE,
                patient().onQueued(notices::incrementAndGet).build());

        assertThat(notices).hasValue(1);
        assertThat(lock).isEqualTo(new LockHolder("lock-1", OptionalLong.of(5), dev.caerus.sdk.dls.LockStatus.ACQUIRED));
    }

    @Test
    void theNoticeArrivesBeforeTheGrantNeverAfter() {
        queueThenGrant();
        List<String> order = new CopyOnWriteArrayList<>();

        client.acquireLock("ns", "k", "tx-1", LockMode.EXCLUSIVE, patient().onQueued(() -> order.add("en cola")).build());
        order.add("concedido");

        assertThat(order).containsExactly("en cola", "concedido");
    }

    @Test
    void doesNotNotifyWhenTheLockIsGrantedRightAway() {
        engine.<AcquireLockRequest, AcquireLockResponse>on("acquireLock", (request, observer) ->
                reply(observer, acquired("lock-1", 5)));
        AtomicInteger notices = new AtomicInteger();

        client.acquireLock("ns", "k", "tx-1", LockMode.EXCLUSIVE,
                AcquireLockOptions.builder().onQueued(notices::incrementAndGet).build());

        assertThat(notices).hasValue(0);
    }

    @Test
    void ifTheCallbackThrowsTheAcquisitionCarriesOnAndItIsLogged() {
        queueThenGrant();
        DlsTestClients.Recorder logger = new DlsTestClients.Recorder();

        try (DlsClient logged = DlsTestClients.real(engine, logger)) {
            LockHolder lock = logged.acquireLock("ns", "k", "tx-1", LockMode.EXCLUSIVE, patient().onQueued(() -> {
                throw new IllegalStateException("la interfaz se rompio");
            }).build());

            assertThat(lock.status()).isEqualTo(dev.caerus.sdk.dls.LockStatus.ACQUIRED);
            assertThat(logger.messages()).containsExactly("onQueued callback threw");
            assertThat(logger.details().get(0)[0]).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void worksTheSameInsideWithTransaction() {
        engine.<BeginTransactionRequest, BeginTransactionResponse>on("beginTransaction", (request, observer) ->
                reply(observer, BeginTransactionResponse.newBuilder().setTransactionId("tx-9").build()));
        engine.<ReleaseTransactionLocksRequest, Empty>on("releaseTransactionLocks", (request, observer) ->
                reply(observer, Empty.getDefaultInstance()));
        queueThenGrant();
        AtomicInteger notices = new AtomicInteger();

        dev.caerus.sdk.dls.LockStatus status = client.withTransaction(tx -> tx.acquireLock("ns", "k", LockMode.EXCLUSIVE,
                        patient().onQueued(notices::incrementAndGet).build()).status(),
                TransactionOptions.builder().autoRenew(false).build());

        assertThat(status).isEqualTo(dev.caerus.sdk.dls.LockStatus.ACQUIRED);
        assertThat(notices).hasValue(1);
        assertThat(engine.lastRequest(ReleaseTransactionLocksRequest.class).getTransactionId()).isEqualTo("tx-9");
    }

    @Test
    void theInMemoryClientAcceptsTheOptionWithoutBreaking() {
        InMemoryDlsClient memory = new InMemoryDlsClient();
        Transaction tx = memory.beginTransaction();
        AtomicInteger notices = new AtomicInteger();

        LockHolder lock = memory.acquireLock("ns", "k", tx.transactionId(), LockMode.EXCLUSIVE,
                AcquireLockOptions.builder().onQueued(notices::incrementAndGet).build());

        assertThat(lock.status()).isEqualTo(dev.caerus.sdk.dls.LockStatus.ACQUIRED);
        assertThat(notices).hasValue(0);
    }
}
