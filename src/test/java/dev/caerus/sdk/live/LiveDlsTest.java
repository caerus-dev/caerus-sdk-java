package dev.caerus.sdk.live;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.dls.AcquireLockOptions;
import dev.caerus.sdk.dls.DeadlockAbortedError;
import dev.caerus.sdk.dls.DlsClient;
import dev.caerus.sdk.dls.LockDeniedError;
import dev.caerus.sdk.dls.LockHolder;
import dev.caerus.sdk.dls.LockMode;
import dev.caerus.sdk.dls.LockStatus;
import dev.caerus.sdk.dls.LockStatusResponse;
import dev.caerus.sdk.dls.Transaction;
import dev.caerus.sdk.dls.TransactionOptions;
import dev.caerus.sdk.dls.TransactionStatusResponse;
import dev.caerus.sdk.dls.BeginTransactionOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Tag("live")
class LiveDlsTest {

    private static final String NS_FAIL = Live.env("DLS_NS_FAIL", "cuenta");
    private static final String NS_QUEUE = Live.env("DLS_NS_QUEUE", "cuenta_cola");
    private static final String NS_ALERT = Live.env("DLS_NS_ALERT", "cuenta_alerta");
    private static final String NS_READ_WRITE = Live.env("DLS_NS_READ_WRITE", "archivo");

    private static DlsClient dls;

    @BeforeAll
    static void connect() {
        dls = Live.dls();
    }

    @AfterAll
    static void disconnect() {
        if (dls != null) {
            dls.close();
        }
    }

    private static AcquireLockOptions.Builder idem() {
        return AcquireLockOptions.builder().idempotencyKey(UUID.randomUUID().toString());
    }

    private static Transaction begin(long timeoutMs) {
        return dls.beginTransaction(BeginTransactionOptions.builder().timeoutMs(timeoutMs).build());
    }

    private static void releaseQuietly(Transaction... transactions) {
        for (Transaction tx : transactions) {
            try {
                dls.releaseTransactionLocks(tx.transactionId());
            } catch (CaerusError ignored) {
            }
        }
    }

    private static Throwable outcome(CompletableFuture<?> future) {
        try {
            future.join();
            return null;
        } catch (CompletionException e) {
            return e.getCause();
        }
    }

    private static CompletableFuture<LockHolder> crossing(String namespace, String first, String second,
                                                          long pauseMs, long waitMs, long lifetimeMs) {
        return CompletableFuture.supplyAsync(() -> dls.withTransaction(tx -> {
            tx.acquireLock(namespace, first, LockMode.EXCLUSIVE, idem().build());
            Live.sleep(pauseMs);
            return tx.acquireLock(namespace, second, LockMode.EXCLUSIVE, idem().timeoutMs(waitMs).build());
        }, TransactionOptions.builder().timeoutMs(lifetimeMs).build()));
    }

    @Test
    void sharedReadAndLockStatus() {
        String file = Live.key("archivo");
        Transaction tx1 = begin(20_000);
        Transaction tx2 = begin(20_000);
        Transaction tx3 = begin(20_000);

        try {
            LockHolder l1 = dls.acquireLock(NS_READ_WRITE, file, tx1.transactionId(), LockMode.SHARED_READ, idem().build());
            LockHolder l2 = dls.acquireLock(NS_READ_WRITE, file, tx2.transactionId(), LockMode.SHARED_READ, idem().build());
            Live.log("dos lectores entran a la vez", l1.status() + " " + l2.status());
            assertThat(l1.status()).isEqualTo(LockStatus.ACQUIRED);
            assertThat(l2.status()).isEqualTo(LockStatus.ACQUIRED);

            LockStatusResponse status = dls.getLockStatus(NS_READ_WRITE, file);
            Live.log("el estado dice SHARED_READ y lista a los dos", status.currentMode() + " " + status.activeHolders().size());
            assertThat(status.currentMode()).contains(LockMode.SHARED_READ);
            assertThat(status.activeHolders()).hasSize(2);
            assertThat(status.activeHolders()).allSatisfy(holder -> assertThat(holder.fencingToken()).isPresent());

            Throwable writer = catchThrowable(() -> dls.acquireLock(NS_READ_WRITE, file, tx3.transactionId(),
                    LockMode.EXCLUSIVE, idem().timeoutMs(4000L).build()));
            Live.log("el escritor no entra con lectores activos", writer == null ? "entro" : writer.getClass().getSimpleName());
            assertThat(writer).isInstanceOf(CaerusError.class);

            dls.releaseLock(l1.lockId(), tx1.transactionId());
            LockStatusResponse after = dls.getLockStatus(NS_READ_WRITE, file);
            Live.log("soltar un lector deja al otro", String.valueOf(after.activeHolders().size()));
            assertThat(after.activeHolders()).hasSize(1);
        } finally {
            releaseQuietly(tx1, tx2, tx3);
        }
    }

    @Test
    void deniedLockInAFailNamespace() {
        String account = Live.key("cuenta");

        dls.withTransaction(tx1 -> {
            LockHolder own = tx1.acquireLock(NS_FAIL, account, LockMode.EXCLUSIVE, idem().build());
            Live.log("el primero lo toma, con fencing token", own.status() + " " + own.fencingToken());
            assertThat(own.status()).isEqualTo(LockStatus.ACQUIRED);
            assertThat(own.fencingToken()).isPresent();

            dls.withTransaction(tx2 -> {
                Throwable error = catchThrowable(() ->
                        tx2.acquireLock(NS_FAIL, account, LockMode.EXCLUSIVE, idem().timeoutMs(5000L).build()));
                Live.log("el segundo recibe LockDeniedError con LOCK_DENIED", String.valueOf(error));
                assertThat(error).isInstanceOf(LockDeniedError.class);
                assertThat(((LockDeniedError) error).reason()).contains("LOCK_DENIED");
                return null;
            }, TransactionOptions.builder().timeoutMs(8000L).build());
            return null;
        }, TransactionOptions.builder().timeoutMs(15_000L).build());
    }

    @Test
    void twoWayDeadlockWithKillPriority() {
        String x = Live.key("X");
        String y = Live.key("Y");
        long started = System.nanoTime();

        CompletableFuture<LockHolder> first = crossing(NS_QUEUE, x, y, 1500, 30_000, 30_000);
        Live.sleep(700);
        CompletableFuture<LockHolder> second = crossing(NS_QUEUE, y, x, 300, 30_000, 30_000);
        List<Throwable> victims = java.util.stream.Stream.of(outcome(first), outcome(second))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        double seconds = (System.nanoTime() - started) / 1e9;

        Live.log("una victima y un ganador", victims.size() + " victimas en " + String.format("%.1f", seconds) + "s");
        assertThat(victims).hasSize(1);
        assertThat(victims.get(0)).isInstanceOf(DeadlockAbortedError.class);
        assertThat(((CaerusError) victims.get(0)).reason()).contains("DEADLOCK_DETECTED");
        assertThat(((CaerusError) victims.get(0)).requestId()).isPresent();
        assertThat(outcome(second)).as("la segunda, la mas joven, es la victima").isNotNull();
        assertThat(seconds).isLessThan(10);
    }

    @Test
    void triangularDeadlock() {
        String x = Live.key("TX");
        String y = Live.key("TY");
        String z = Live.key("TZ");

        List<CompletableFuture<LockHolder>> runs = List.of(
                crossing(NS_QUEUE, x, y, 400, 30_000, 30_000),
                crossing(NS_QUEUE, y, z, 400, 30_000, 30_000),
                crossing(NS_QUEUE, z, x, 400, 30_000, 30_000));
        List<Throwable> victims = runs.stream().map(LiveDlsTest::outcome)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        Live.log("se rompe el ciclo sin matar a todos", victims.size() + " de 3");
        assertThat(victims).hasSizeBetween(1, 2);
        assertThat(victims).allSatisfy(victim -> assertThat(((CaerusError) victim).reason()).contains("DEADLOCK_DETECTED"));
    }

    @Test
    void deadlockWithAlertStrategyAbortsNobody() {
        String x = Live.key("LX");
        String y = Live.key("LY");

        List<Throwable> results = java.util.stream.Stream.of(
                        crossing(NS_ALERT, x, y, 300, 6000, 20_000),
                        crossing(NS_ALERT, y, x, 300, 6000, 20_000))
                .map(LiveDlsTest::outcome)
                .collect(Collectors.toList());
        long aborted = results.stream()
                .filter(error -> error instanceof CaerusError caerus && caerus.reason().orElse("").equals("DEADLOCK_DETECTED"))
                .count();

        Live.log("nadie fue abortado por deadlock", aborted + " abortadas");
        assertThat(aborted).isZero();
    }

    @Test
    void transactionStatusWithALockInTheQueueAndOnQueued() {
        String key = Live.key("cola");
        Transaction tx1 = begin(20_000);
        Transaction tx2 = begin(20_000);

        try {
            dls.acquireLock(NS_QUEUE, key, tx1.transactionId(), LockMode.EXCLUSIVE, idem().build());
            AtomicInteger notices = new AtomicInteger();
            AtomicBoolean grantedBeforeNotice = new AtomicBoolean();
            List<String> order = new CopyOnWriteArrayList<>();
            CompletableFuture<LockHolder> waiting = CompletableFuture.supplyAsync(() -> {
                LockHolder lock = dls.acquireLock(NS_QUEUE, key, tx2.transactionId(), LockMode.EXCLUSIVE, idem()
                        .timeoutMs(8000L)
                        .onQueued(() -> {
                            notices.incrementAndGet();
                            order.add("en cola");
                        })
                        .build());
                grantedBeforeNotice.set(notices.get() == 0);
                order.add("concedido");
                return lock;
            });
            Live.sleep(1500);

            TransactionStatusResponse holding = dls.getTransactionStatus(tx1.transactionId());
            TransactionStatusResponse queued = dls.getTransactionStatus(tx2.transactionId());
            Live.log("las dos transacciones informan su estado", holding.status() + " / " + queued.status());
            assertThat(holding.status()).isNotBlank();
            assertThat(queued.status()).isNotBlank();

            dls.releaseTransactionLocks(tx1.transactionId());
            LockHolder granted = waiting.join();

            Live.log("onQueued avisa una vez y antes de conceder", notices.get() + " avisos, orden " + order);
            assertThat(granted.status()).isEqualTo(LockStatus.ACQUIRED);
            assertThat(notices).hasValue(1);
            assertThat(grantedBeforeNotice).isFalse();
            assertThat(order).containsExactly("en cola", "concedido");
        } finally {
            releaseQuietly(tx1, tx2);
        }
    }

    @Test
    void aQueuedLockWithoutItsOwnTimeoutUsesTheClientDeadline() {
        String key = Live.key("plazo");
        Transaction tx1 = begin(30_000);
        Transaction tx2 = begin(30_000);

        try (DlsClient impatient = Live.dls(2000L)) {
            dls.acquireLock(NS_QUEUE, key, tx1.transactionId(), LockMode.EXCLUSIVE, idem().build());
            long started = System.nanoTime();
            Throwable error = catchThrowable(() ->
                    impatient.acquireLock(NS_QUEUE, key, tx2.transactionId(), LockMode.EXCLUSIVE, idem().build()));
            double seconds = (System.nanoTime() - started) / 1e9;

            Live.log("sin timeout propio corta con el del cliente", error + " en " + String.format("%.1f", seconds) + "s");
            assertThat(error).isInstanceOf(dev.caerus.sdk.dls.DlsTimeoutError.class);
            assertThat(seconds).isBetween(1.5, 5.0);
        } finally {
            releaseQuietly(tx1, tx2);
        }
    }

    @Test
    void aClosedTransactionCannotTakeLocks() {
        Transaction tx = begin(20_000);
        dls.releaseTransactionLocks(tx.transactionId());

        Throwable error = catchThrowable(() ->
                dls.acquireLock(NS_QUEUE, Live.key("cerrada"), tx.transactionId(), LockMode.EXCLUSIVE, idem().build()));

        Live.log("una transaccion cerrada no toma locks", String.valueOf(error));
        assertThat(error).isInstanceOf(CaerusError.class);
        assertThat(((CaerusError) error).reason()).contains("TRANSACTION_NOT_ACTIVE");
    }

    @Test
    void withTransactionRenewsALongJob() {
        String key = Live.key("largo");

        long[] expiresAt = new long[2];
        String result = dls.withTransaction(tx -> {
            tx.acquireLock(NS_QUEUE, key, LockMode.EXCLUSIVE, idem().build());
            expiresAt[0] = dls.getTransactionStatus(tx.transactionId()).expiresAt();
            Live.sleep(7000);
            TransactionStatusResponse status = dls.getTransactionStatus(tx.transactionId());
            expiresAt[1] = status.expiresAt();
            return status.status();
        }, TransactionOptions.builder().timeoutMs(4000L).build());

        Live.log("una transaccion de 4 s sigue viva a los 7 s y su vencimiento avanza",
                result + ", +" + (expiresAt[1] - expiresAt[0]) + " ms");
        assertThat(result).isEqualTo("ACTIVE");
        assertThat(expiresAt[1] - expiresAt[0]).isGreaterThanOrEqualTo(8000);
    }

    @Test
    void aWrongApiKeyIsAnAuthenticationError() {
        try (DlsClient stranger = new DlsClient(dev.caerus.sdk.dls.DlsClientOptions.builder()
                .apiKey("caer_dev_no_existe_" + UUID.randomUUID())
                .endpoint(Live.endpoint())
                .tls(Live.tls())
                .build())) {
            Throwable error = catchThrowable(stranger::beginTransaction);

            Live.log("una API Key que no existe da error de autenticacion", String.valueOf(error));
            assertThat(error).isInstanceOf(dev.caerus.sdk.dls.DlsAuthenticationError.class);
            assertThat(((CaerusError) error).requestId()).isPresent();
        }
    }

    @Test
    void anEngineErrorCarriesARequestId() {
        Throwable error = catchThrowable(() -> dls.renewTransaction(UUID.randomUUID().toString(), 1000));

        Live.log("el error trae el requestId", String.valueOf(error));
        assertThat(error).isInstanceOf(CaerusError.class);
        assertThat(((CaerusError) error).requestId()).hasValueSatisfying(id -> assertThat(id).matches("^req_[0-9a-f-]{36}$"));
    }

    @Test
    void fencingTokensGrowAcrossHandoffs() {
        String key = Live.key("fencing");
        List<Long> tokens = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 3; i++) {
            dls.withTransaction(tx -> {
                tokens.add(tx.acquireLock(NS_QUEUE, key, LockMode.EXCLUSIVE, idem().build()).fencingToken().orElseThrow());
                return null;
            }, TransactionOptions.builder().timeoutMs(10_000L).autoRenew(false).build());
        }

        Live.log("los fencing tokens crecen", tokens.toString());
        assertThat(tokens).isSorted().doesNotHaveDuplicates();
    }
}
