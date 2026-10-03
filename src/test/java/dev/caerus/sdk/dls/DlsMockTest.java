package dev.caerus.sdk.dls;

import dev.caerus.sdk.AbortController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class DlsMockTest {

    private InMemoryDlsClient client;

    @BeforeEach
    void create() {
        client = new InMemoryDlsClient();
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
    void canBeginATransaction() {
        Transaction tx = client.beginTransaction();

        assertThat(tx.transactionId()).startsWith("tx-mock-");
        assertThat(client.getTransactionStatus(tx.transactionId()).status()).isEqualTo("ACTIVE");
    }

    @Test
    void throwsWhenGettingTheStatusOfATransactionThatDoesNotExist() {
        assertThatThrownBy(() -> client.getTransactionStatus("invalid")).isInstanceOf(DlsNotFoundError.class);
    }

    @Test
    void canAcquireAndReleaseAnExclusiveLock() {
        Transaction tx = client.beginTransaction();
        LockHolder lock = client.acquireLock("namespace", "key1", tx.transactionId(), LockMode.EXCLUSIVE);

        assertThat(lock.status()).isEqualTo(LockStatus.ACQUIRED);
        assertThat(lock.lockId()).isNotBlank();
        LockStatusResponse held = client.getLockStatus("namespace", "key1");
        assertThat(held.isHeld()).isTrue();
        assertThat(held.currentMode()).contains(LockMode.EXCLUSIVE);

        client.releaseLock(lock.lockId(), tx.transactionId());

        assertThat(client.getLockStatus("namespace", "key1").isHeld()).isFalse();
    }

    @Test
    void handsOutIncreasingFencingTokens() {
        Transaction tx = client.beginTransaction();
        LockHolder first = client.acquireLock("ns", "a", tx.transactionId(), LockMode.EXCLUSIVE);
        LockHolder second = client.acquireLock("ns", "b", tx.transactionId(), LockMode.EXCLUSIVE);

        assertThat(second.fencingToken().getAsLong()).isGreaterThan(first.fencingToken().getAsLong());
        assertThat(client.getLockStatus("ns", "a").activeHolders().get(0).fencingToken()).isEqualTo(first.fencingToken());
    }

    @Test
    void deniesAnExclusiveLockAlreadyHeldByAnotherTransaction() {
        Transaction tx1 = client.beginTransaction();
        Transaction tx2 = client.beginTransaction();

        client.acquireLock("namespace", "key1", tx1.transactionId(), LockMode.EXCLUSIVE);

        assertThatThrownBy(() -> client.acquireLock("namespace", "key1", tx2.transactionId(), LockMode.EXCLUSIVE))
                .isInstanceOf(LockDeniedError.class)
                .satisfies(error -> assertThat(((LockDeniedError) error).reason()).contains("LOCK_DENIED"));
    }

    @Test
    void allowsSharedReadLocksByMultipleTransactions() {
        Transaction tx1 = client.beginTransaction();
        Transaction tx2 = client.beginTransaction();

        client.acquireLock("namespace", "key1", tx1.transactionId(), LockMode.SHARED_READ);
        client.acquireLock("namespace", "key1", tx2.transactionId(), LockMode.SHARED_READ);

        LockStatusResponse status = client.getLockStatus("namespace", "key1");
        assertThat(status.isHeld()).isTrue();
        assertThat(status.currentMode()).contains(LockMode.SHARED_READ);
        assertThat(status.activeHolders()).hasSize(2);
    }

    @Test
    void canReleaseAllTheLocksOfATransactionAtOnce() {
        Transaction tx = client.beginTransaction();
        client.acquireLock("ns1", "key1", tx.transactionId(), LockMode.EXCLUSIVE);
        client.acquireLock("ns2", "key2", tx.transactionId(), LockMode.SHARED_READ);

        client.releaseTransactionLocks(tx.transactionId());

        assertThat(client.getLockStatus("ns1", "key1").isHeld()).isFalse();
        assertThat(client.getLockStatus("ns2", "key2").isHeld()).isFalse();
        assertThatThrownBy(() -> client.getTransactionStatus(tx.transactionId())).isInstanceOf(DlsNotFoundError.class);
    }

    @Test
    void canRenewATransaction() {
        Transaction tx = client.beginTransaction(BeginTransactionOptions.builder().timeoutMs(1000L).build());
        long initial = client.getTransactionStatus(tx.transactionId()).expiresAt();

        client.renewTransaction(tx.transactionId(), 5000);

        assertThat(client.getTransactionStatus(tx.transactionId()).expiresAt()).isEqualTo(initial + 5000);
    }

    @Test
    void renewingATransactionThatDoesNotExistThrows() {
        assertThatThrownBy(() -> client.renewTransaction("ghost", 1000)).isInstanceOf(DlsNotFoundError.class);
    }

    @Test
    void releasingALockOfATransactionThatDoesNotExistDoesNothingLikeTheEngine() {
        assertThatCode(() -> client.releaseLock("l-1", "ghost")).doesNotThrowAnyException();
    }

    @Test
    void releasingALockThatDoesNotExistDoesNothingLikeTheEngine() {
        Transaction tx = client.beginTransaction();

        assertThatCode(() -> client.releaseLock("ghost-lock", tx.transactionId())).doesNotThrowAnyException();
    }

    @Test
    void releasingSomeoneElsesLockLeavesItHeld() {
        Transaction owner = client.beginTransaction();
        Transaction other = client.beginTransaction();
        LockHolder lock = client.acquireLock("ns", "k", owner.transactionId(), LockMode.EXCLUSIVE);

        client.releaseLock(lock.lockId(), other.transactionId());

        assertThat(client.getLockStatus("ns", "k").isHeld()).isTrue();
    }

    @Test
    void releasingTheTransactionLocksCleansUp() {
        Transaction tx = client.beginTransaction();
        client.acquireLock("ns1", "k1", tx.transactionId(), LockMode.EXCLUSIVE);

        client.releaseTransactionLocks(tx.transactionId());

        assertThat(client.getLockStatus("ns1", "k1").isHeld()).isFalse();
    }

    @Test
    void deniesSharedReadIfAnExclusiveLockIsHeld() {
        Transaction tx1 = client.beginTransaction();
        client.acquireLock("ns", "key", tx1.transactionId(), LockMode.EXCLUSIVE);
        Transaction tx2 = client.beginTransaction();

        assertThatThrownBy(() -> client.acquireLock("ns", "key", tx2.transactionId(), LockMode.SHARED_READ))
                .isInstanceOf(LockDeniedError.class);
    }

    @Test
    void deniesExclusiveIfSharedReadIsHeldBySomeoneElse() {
        Transaction tx1 = client.beginTransaction();
        client.acquireLock("ns", "key", tx1.transactionId(), LockMode.SHARED_READ);
        Transaction tx2 = client.beginTransaction();

        assertThatThrownBy(() -> client.acquireLock("ns", "key", tx2.transactionId(), LockMode.EXCLUSIVE))
                .isInstanceOf(LockDeniedError.class);
    }

    @Test
    void getTransactionStatusReturnsEveryLockOfTheTransaction() {
        Transaction tx = client.beginTransaction();
        client.acquireLock("ns", "key1", tx.transactionId(), LockMode.EXCLUSIVE);
        client.acquireLock("ns", "key2", tx.transactionId(), LockMode.SHARED_READ);

        TransactionStatusResponse status = client.getTransactionStatus(tx.transactionId());

        assertThat(status.locks()).hasSize(2);
        assertThat(status.locks()).anySatisfy(lock -> {
            assertThat(lock.lockKey()).isEqualTo("key1");
            assertThat(lock.requestedMode()).isEqualTo(LockMode.EXCLUSIVE);
        });
        assertThat(status.locks()).anySatisfy(lock -> {
            assertThat(lock.lockKey()).isEqualTo("key2");
            assertThat(lock.requestedMode()).isEqualTo(LockMode.SHARED_READ);
        });
    }

    @Test
    void theSameIdempotencyKeyReplaysTheSameLockLikeTheEngine() {
        Transaction tx = client.beginTransaction();
        AcquireLockOptions options = AcquireLockOptions.builder().idempotencyKey("idem-1").build();

        LockHolder first = client.acquireLock("ns", "k", tx.transactionId(), LockMode.EXCLUSIVE, options);
        LockHolder again = client.acquireLock("ns", "k", tx.transactionId(), LockMode.EXCLUSIVE, options);

        assertThat(again).isEqualTo(first);
        assertThat(client.getLockStatus("ns", "k").activeHolders()).hasSize(1);
    }

    @Test
    void anotherKeyForALockTheTransactionAlreadyHasIsAlreadyHeld() {
        Transaction tx = client.beginTransaction();
        client.acquireLock("ns", "k", tx.transactionId(), LockMode.EXCLUSIVE,
                AcquireLockOptions.builder().idempotencyKey("idem-1").build());

        assertThatThrownBy(() -> client.acquireLock("ns", "k", tx.transactionId(), LockMode.EXCLUSIVE,
                AcquireLockOptions.builder().idempotencyKey("idem-2").build()))
                .isInstanceOf(LockAlreadyHeldError.class)
                .satisfies(error -> assertThat(((LockAlreadyHeldError) error).reason())
                        .contains("LOCK_ALREADY_HELD_EXCLUSIVELY"));
        assertThatThrownBy(() -> client.acquireLock("ns", "k", tx.transactionId(), LockMode.EXCLUSIVE))
                .isInstanceOf(LockAlreadyHeldError.class);
    }

    @Test
    void theIdempotencyKeyOfAnotherTransactionDoesNotReplay() {
        Transaction tx1 = client.beginTransaction();
        Transaction tx2 = client.beginTransaction();
        AcquireLockOptions options = AcquireLockOptions.builder().idempotencyKey("idem-1").build();
        client.acquireLock("ns", "k", tx1.transactionId(), LockMode.EXCLUSIVE, options);

        assertThatThrownBy(() -> client.acquireLock("ns", "k", tx2.transactionId(), LockMode.EXCLUSIVE, options))
                .isInstanceOf(LockDeniedError.class);
    }

    @Test
    void refusesALockForATransactionThatDoesNotExist() {
        assertThatThrownBy(() -> client.acquireLock("ns", "k", "ghost", LockMode.EXCLUSIVE))
                .isInstanceOf(DlsNotFoundError.class);
    }

    @Test
    void withTransactionRenewsOnItsOwnAndReleasesAtTheEnd() {
        AtomicReference<String> txId = new AtomicReference<>();
        AtomicReference<Long> before = new AtomicReference<>();
        AtomicReference<Long> after = new AtomicReference<>();

        String result = client.withTransaction(tx -> {
            txId.set(tx.transactionId());
            tx.acquireLock("ns1", "key1", LockMode.EXCLUSIVE);
            before.set(client.getTransactionStatus(tx.transactionId()).expiresAt());
            sleep(1300);
            after.set(client.getTransactionStatus(tx.transactionId()).expiresAt());
            return "done";
        }, TransactionOptions.builder().timeoutMs(2000L).build());

        assertThat(result).isEqualTo("done");
        assertThat(after.get() - before.get()).isGreaterThanOrEqualTo(2000);
        assertThat(client.getLockStatus("ns1", "key1").isHeld()).isFalse();
        assertThatThrownBy(() -> client.getTransactionStatus(txId.get())).isInstanceOf(DlsNotFoundError.class);
    }

    @Test
    void withTransactionReleasesOnErrorAndRethrows() {
        assertThatThrownBy(() -> client.withTransaction(tx -> {
            tx.acquireLock("ns2", "key2", LockMode.EXCLUSIVE);
            throw new IllegalStateException("Business logic failed");
        })).hasMessage("Business logic failed");

        assertThat(client.getLockStatus("ns2", "key2").isHeld()).isFalse();
    }

    @Test
    void preventsAcquireLockOnceTheContextIsClosed() {
        AtomicReference<TransactionContext> leaked = new AtomicReference<>();

        client.withTransaction(tx -> {
            leaked.set(tx);
            return null;
        });

        assertThatThrownBy(() -> leaked.get().acquireLock("ns", "key", LockMode.EXCLUSIVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Transaction context already closed");
    }

    @Test
    void abortsAcquireLockIfTheSignalIsAborted() {
        AbortController controller = new AbortController();
        controller.abort();

        assertThatThrownBy(() -> client.withTransaction(tx -> tx.acquireLock("ns3", "key3", LockMode.EXCLUSIVE),
                TransactionOptions.builder().signal(controller.signal()).build()))
                .isInstanceOf(DlsError.class)
                .hasMessageContaining("AcquireLock aborted by user");
    }

    @Test
    void ifRenewingFailsBeyondRepairTheTransactionIsLost() {
        AtomicReference<DlsError> notified = new AtomicReference<>();

        Throwable error = catchThrowable(() -> client.withTransaction(tx -> {
            client.releaseTransactionLocks(tx.transactionId());
            sleep(1500);
            return "el callback termino igual";
        }, TransactionOptions.builder().timeoutMs(2000L).onTransactionLost(notified::set).build()));

        assertThat(error).isInstanceOf(DlsNotFoundError.class);
        assertThat(notified.get()).isInstanceOf(DlsNotFoundError.class);
    }

    @Test
    void onceLostItWillNotTakeMoreLocksNorReturnAGoodResult() {
        AtomicReference<Throwable> onTake = new AtomicReference<>();
        AtomicBoolean abortNotice = new AtomicBoolean();

        Throwable error = catchThrowable(() -> client.withTransaction(tx -> {
            tx.signal().onAbort(() -> abortNotice.set(true));
            client.releaseTransactionLocks(tx.transactionId());
            sleep(1500);
            onTake.set(catchThrowable(() -> tx.acquireLock("ns", "k", LockMode.EXCLUSIVE)));
            return "no deberia llegar";
        }, TransactionOptions.builder().timeoutMs(2000L).build()));

        assertThat(error).isInstanceOf(DlsNotFoundError.class);
        assertThat(onTake.get()).isInstanceOf(DlsNotFoundError.class);
        assertThat(abortNotice).isTrue();
    }

    @Test
    void rejectsCallsAfterBeingClosed() {
        client.close();

        assertThatThrownBy(client::beginTransaction).isInstanceOf(DlsError.class).hasMessageContaining("closed");
    }

    @Test
    void isUsableWhereverTheRealClientIs() {
        DlsApi api = client;

        String result = api.withTransaction(tx -> tx.acquireLock("ns", "k", LockMode.EXCLUSIVE).status().name());

        assertThat(result).isEqualTo("ACQUIRED");
    }
}
