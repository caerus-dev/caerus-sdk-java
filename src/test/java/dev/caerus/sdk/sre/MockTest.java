package dev.caerus.sdk.sre;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class MockTest {

    private static InMemoryCaerusClient aMock(int availableAmount) {
        return new InMemoryCaerusClient(MockOptions.builder()
                .resources(List.of(MockResourceSeed.builder("seat_A12", availableAmount).groupKey("row_A").build()))
                .build());
    }

    private static InMemoryCaerusClient aMock() {
        return aMock(1);
    }

    private static TakeOptions ttl(int seconds) {
        return TakeOptions.builder().ttlSeconds(seconds).build();
    }

    private static String checkout(SharedResourceApi caerus) {
        ResourceHolder holder = caerus.unitary("seat_A12").take();
        caerus.confirm(holder.id());
        return holder.id();
    }

    @Test
    void isUsableWhereverTheRealClientIs() {
        assertThat(checkout(aMock())).startsWith("hld-");
    }

    @Test
    void movesUnitsFromAvailableToPendingWhenTaken() {
        InMemoryCaerusClient caerus = aMock(5);

        caerus.pooled("seat_A12").takeMany(2);
        Resource resource = caerus.getResource("seat_A12");

        assertThat(resource.availableAmount()).isEqualTo(3);
        assertThat(resource.pendingCount()).isEqualTo(2);
    }

    @Test
    void givesThemBackOnRelease() {
        InMemoryCaerusClient caerus = aMock(5);

        ResourceHolder holder = caerus.pooled("seat_A12").takeMany(2);
        caerus.release(holder.id());

        Resource resource = caerus.getResource("seat_A12");
        assertThat(resource.availableAmount()).isEqualTo(5);
        assertThat(resource.pendingCount()).isZero();
    }

    @Test
    void keepsThemTakenOnConfirm() {
        InMemoryCaerusClient caerus = aMock(5);

        ResourceHolder holder = caerus.pooled("seat_A12").takeMany(2);
        caerus.confirm(holder.id());

        Resource resource = caerus.getResource("seat_A12");
        assertThat(resource.availableAmount()).isEqualTo(3);
        assertThat(resource.pendingCount()).isZero();
    }

    @Test
    void refusesToTakeMoreThanExists() {
        InMemoryCaerusClient caerus = aMock(1);

        caerus.unitary("seat_A12").take();

        assertThatThrownBy(() -> caerus.unitary("seat_A12").take()).isInstanceOf(ConflictError.class);
    }

    @Test
    void reportsItAsTheEngineDoesDownToTheErrorType() {
        InMemoryCaerusClient caerus = aMock(1);
        caerus.unitary("seat_A12").take();

        OutOfStockError error = catchThrowableOfType(OutOfStockError.class, () -> caerus.unitary("seat_A12").take());

        assertThat(error).isInstanceOf(ConflictError.class);
        assertThat(error.code()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(error.getMessage()).isEqualTo("Out of stock for resource: seat_A12");
    }

    @Test
    void appliesTheSameRuleToTakeMany() {
        assertThatThrownBy(() -> aMock(3).pooled("seat_A12").takeMany(4)).isInstanceOf(ConflictError.class);
    }

    @Test
    void doesNotKnowAboutResourcesNobodyCreated() {
        assertThatThrownBy(() -> aMock().unitary("seat_ZZZ").take()).isInstanceOf(ResourceNotFoundError.class);
    }

    @Test
    void createsResourcesThroughTheApiToo() {
        InMemoryCaerusClient caerus = new InMemoryCaerusClient();

        Resource created = caerus.createMultiple("seat", "seat_B1", 3);
        ResourceHolder holder = caerus.unitary("seat_B1").take();

        assertThat(created.availableAmount()).isEqualTo(3);
        assertThat(created.templateId()).isEqualTo("tpl-seat");
        assertThat(holder.status()).isEqualTo(ResourceHolderStatus.PENDING);
    }

    @Test
    void refusesToCreateTheSameKeyTwice() {
        assertThatThrownBy(() -> aMock().createMultiple("seat", "seat_A12", 1)).isInstanceOf(ConflictError.class);
    }

    @Test
    void givesHoldersARealExpiresAt() {
        ResourceHolder holder = aMock().unitary("seat_A12").take(ttl(600));

        long secondsAway = Duration.between(Instant.now(), holder.expiresAt()).getSeconds();
        assertThat(secondsAway).isBetween(590L, 610L);
    }

    @Test
    void doesNotExpireAnythingOnItsOwn() throws InterruptedException {
        InMemoryCaerusClient caerus = aMock();

        ResourceHolder holder = caerus.unitary("seat_A12").take(ttl(1));
        Thread.sleep(30);

        assertThat(caerus.getResourceHolder(holder.id()).status()).isEqualTo(ResourceHolderStatus.PENDING);
    }

    @Test
    void expiresWhatIsPastDueWhenTheClockMoves() {
        InMemoryCaerusClient caerus = aMock();

        ResourceHolder holder = caerus.unitary("seat_A12").take(ttl(300));
        caerus.advanceTime(301);

        assertThat(caerus.getResourceHolder(holder.id()).status()).isEqualTo(ResourceHolderStatus.EXPIRED);
    }

    @Test
    void leavesAloneWhatIsNotDueYet() {
        InMemoryCaerusClient caerus = aMock();

        ResourceHolder holder = caerus.unitary("seat_A12").take(ttl(300));
        caerus.advanceTime(299);

        assertThat(caerus.getResourceHolder(holder.id()).status()).isEqualTo(ResourceHolderStatus.PENDING);
    }

    @Test
    void returnsTheUnitsWhenAHolderExpires() {
        InMemoryCaerusClient caerus = aMock(2);

        caerus.pooled("seat_A12").takeMany(2);
        caerus.advanceTime(1000);

        Resource resource = caerus.getResource("seat_A12");
        assertThat(resource.availableAmount()).isEqualTo(2);
        assertThat(resource.pendingCount()).isZero();
    }

    @Test
    void expiresOneHolderOnDemand() {
        InMemoryCaerusClient caerus = aMock();

        ResourceHolder holder = caerus.unitary("seat_A12").take();
        caerus.expire(holder.id());

        assertThat(caerus.getResourceHolder(holder.id()).status()).isEqualTo(ResourceHolderStatus.EXPIRED);
    }

    @Test
    void refusesToExpireAHolderThatIsNotPending() {
        InMemoryCaerusClient caerus = aMock();

        ResourceHolder holder = caerus.unitary("seat_A12").take();
        caerus.release(holder.id());

        assertThatThrownBy(() -> caerus.expire(holder.id())).isInstanceOf(HolderNotActiveError.class);
    }

    @Test
    void refusesToConfirmOneThatExpired() {
        InMemoryCaerusClient caerus = aMock();

        ResourceHolder holder = caerus.unitary("seat_A12").take();
        caerus.expire(holder.id());

        assertThatThrownBy(() -> caerus.confirm(holder.id())).isInstanceOf(ConflictError.class);
    }

    @Test
    void extendsTheExpiryByTheMillisecondsItIsGiven() {
        InMemoryCaerusClient caerus = aMock();

        ResourceHolder taken = caerus.unitary("seat_A12").take(ttl(300));
        ResourceHolder extended = caerus.extend(taken.id(), 300_000);
        caerus.advanceTime(301);

        assertThat(Duration.between(taken.expiresAt(), extended.expiresAt())).isEqualTo(Duration.ofMillis(300_000));
        assertThat(caerus.getResourceHolder(taken.id()).status()).isEqualTo(ResourceHolderStatus.PENDING);
    }

    @Test
    void roundsASubSecondExtensionUpToOneSecond() {
        InMemoryCaerusClient caerus = aMock();

        ResourceHolder taken = caerus.unitary("seat_A12").take(ttl(300));
        ResourceHolder extended = caerus.extend(taken.id(), 300);

        assertThat(Duration.between(taken.expiresAt(), extended.expiresAt())).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void refusesANegativeJump() {
        assertThatThrownBy(() -> aMock().advanceTime(-1)).isInstanceOf(ValidationError.class);
    }

    @Test
    void failsTheNextCallToTheNamedMethod() {
        InMemoryCaerusClient caerus = aMock();
        caerus.failNext(MockMethod.TAKE, new ConflictError("Out of stock for resource: seat_A12"));

        assertThatThrownBy(() -> caerus.unitary("seat_A12").take()).hasMessageContaining("Out of stock");
        assertThat(caerus.unitary("seat_A12").take().status()).isEqualTo(ResourceHolderStatus.PENDING);
    }

    @Test
    void queuesOneFailurePerCall() {
        InMemoryCaerusClient caerus = aMock(5);
        caerus.failNext(MockMethod.TAKE, new ConflictError("first"));
        caerus.failNext(MockMethod.TAKE, new ConflictError("second"));

        assertThatThrownBy(() -> caerus.unitary("seat_A12").take()).hasMessage("first");
        assertThatThrownBy(() -> caerus.unitary("seat_A12").take()).hasMessage("second");
        assertThat(caerus.unitary("seat_A12").take()).isNotNull();
    }

    @Test
    void forgetsThemWhenAsked() {
        InMemoryCaerusClient caerus = aMock();
        caerus.failNext(MockMethod.TAKE, new ConflictError("nope"));
        caerus.clearFailures();

        assertThat(caerus.unitary("seat_A12").take()).isNotNull();
    }

    @Test
    void letsACallerExerciseAReleaseThatFails() {
        InMemoryCaerusClient caerus = aMock();
        ResourceHolder holder = caerus.unitary("seat_A12").take();
        caerus.failNext(MockMethod.RELEASE, new ConflictError("release exploded"));

        assertThatThrownBy(() -> caerus.release(holder.id())).hasMessage("release exploded");
    }

    @Test
    void takeManyFailsSeparatelyFromTake() {
        InMemoryCaerusClient caerus = aMock(5);
        caerus.failNext(MockMethod.TAKE_MANY, new ConflictError("many"));

        assertThat(caerus.pooled("seat_A12").take()).isNotNull();
        assertThatThrownBy(() -> caerus.pooled("seat_A12").takeMany(2)).hasMessage("many");
    }

    @Test
    void keepsTheUnitsTakenAfterAConfirm() {
        InMemoryCaerusClient caerus = aMock();

        ResourceHolder holder = caerus.unitary("seat_A12").take();
        caerus.confirm(holder.id());

        Resource resource = caerus.getResource("seat_A12");
        assertThat(resource.availableAmount()).isZero();
        assertThat(resource.pendingCount()).isZero();
    }

    @Test
    void reportsExpiredFromAQueryRatherThanThrowing() {
        InMemoryCaerusClient caerus = aMock();

        ResourceHolder holder = caerus.unitary("seat_A12").take();
        caerus.expire(holder.id());

        assertThat(caerus.getResourceHolder(holder.id()).status()).isEqualTo(ResourceHolderStatus.EXPIRED);
    }

    @Test
    void givesBackTheFirstHolderForARepeatedIdempotencyKey() {
        InMemoryCaerusClient caerus = aMock(5);
        TakeOptions options = TakeOptions.builder().idempotencyKey("order-1").build();

        ResourceHolder first = caerus.unitary("seat_A12").take(options);
        ResourceHolder again = caerus.unitary("seat_A12").take(options);

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(caerus.getResource("seat_A12").pendingCount()).isEqualTo(1);
    }

    @Test
    void keepsMetadataAsAnObject() {
        ResourceHolder holder = aMock().unitary("seat_A12")
                .take(TakeOptions.builder().metadata(Map.of("orderId", "12345")).build());

        assertThat(holder.metadata()).contains(Map.of("orderId", "12345"));
    }

    @Test
    void rejectsAZeroAmountTheSameWay() {
        assertThatThrownBy(() -> aMock().pooled("seat_A12").takeMany(0)).isInstanceOf(ValidationError.class);
    }

    @Test
    void rejectsAZeroExtensionTheSameWay() {
        assertThatThrownBy(() -> aMock().extend("hld-1", 0)).isInstanceOf(ValidationError.class);
    }

    @Test
    void rejectsCallsAfterBeingClosed() {
        InMemoryCaerusClient caerus = aMock();
        caerus.close();

        assertThatThrownBy(() -> caerus.unitary("seat_A12").take())
                .isExactlyInstanceOf(CaerusError.class)
                .hasMessageContaining("closed");
    }

    @Test
    void showsItsStateForAssertionsTheApiCannotMake() {
        InMemoryCaerusClient caerus = aMock(3);
        caerus.unitary("seat_A12").take();

        MockSnapshot snapshot = caerus.snapshot();

        assertThat(snapshot.resources()).hasSize(1);
        assertThat(snapshot.holders()).hasSize(1);
        assertThat(snapshot.holders().get(0).status()).isEqualTo(ResourceHolderStatus.PENDING);
    }

    @Test
    void pagesAGroup() {
        InMemoryCaerusClient caerus = new InMemoryCaerusClient(MockOptions.builder()
                .resources(List.of(
                        MockResourceSeed.builder("a", 1).groupKey("row_A").build(),
                        MockResourceSeed.builder("b", 1).groupKey("row_A").build(),
                        MockResourceSeed.builder("c", 1).groupKey("row_B").build()))
                .build());

        ResourcePage first = caerus.getResourcesByGroup("row_A",
                GetResourcesByGroupOptions.builder().page(0).pageSize(1).build());
        ResourcePage second = caerus.getResourcesByGroup("row_A",
                GetResourcesByGroupOptions.builder().page(1).pageSize(1).build());

        assertThat(first.resources().stream().map(Resource::key).collect(Collectors.toList())).containsExactly("a");
        assertThat(first.hasNextPage()).isTrue();
        assertThat(second.resources().stream().map(Resource::key).collect(Collectors.toList())).containsExactly("b");
        assertThat(second.hasNextPage()).isFalse();
    }

    @Test
    void updatesTheResourceAvailableAmount() {
        Resource updated = aMock(10).updateResource("seat_A12", 5, UpdateResourceOptions.builder().groupKey("row_X").build());

        assertThat(updated.availableAmount()).isEqualTo(15);
        assertThat(updated.groupKey()).contains("row_X");
    }

    @Test
    void refusesAnUpdateThatResultsInNegativeStock() {
        assertThatThrownBy(() -> aMock(5).updateResource("seat_A12", -10)).isInstanceOf(ConflictError.class);
    }

    @Test
    void deletesAResourceWithNoActiveHolds() {
        InMemoryCaerusClient caerus = aMock(5);

        caerus.deleteResource("seat_A12");

        assertThatThrownBy(() -> caerus.getResource("seat_A12")).hasMessageContaining("not found");
    }

    @Test
    void refusesToDeleteAResourceWithPendingHolds() {
        InMemoryCaerusClient caerus = aMock(5);
        caerus.unitary("seat_A12").take();

        assertThatThrownBy(() -> caerus.deleteResource("seat_A12")).isInstanceOf(ResourceHasActiveHoldsError.class);
    }

    private static final class ThreeHolders {
        private final InMemoryCaerusClient caerus = aMock(10);
        private final ResourceHolder first = caerus.pooled("seat_A12").take();
        private final ResourceHolder second = caerus.pooled("seat_A12").take();
        private final ResourceHolder third = caerus.pooled("seat_A12").take();

        private ThreeHolders() {
            caerus.confirm(second.id());
        }
    }

    @Test
    void listsNewestFirstByDefault() {
        ThreeHolders three = new ThreeHolders();

        ResourceHolderPage page = three.caerus.listResourceHolders();

        assertThat(page.holders()).hasSize(3);
        assertThat(page.holders().get(0).id()).isEqualTo(three.third.id());
        assertThat(page.holders().get(2).id()).isEqualTo(three.first.id());
    }

    @Test
    void canBeAskedForTheOtherOrder() {
        ThreeHolders three = new ThreeHolders();

        ResourceHolderPage page = three.caerus.listResourceHolders(
                ListResourceHoldersOptions.builder().sort(HolderSort.OLDEST_FIRST).build());

        assertThat(page.holders().get(0).id()).isEqualTo(three.first.id());
    }

    @Test
    void narrowsByStatus() {
        ThreeHolders three = new ThreeHolders();

        ResourceHolderPage confirmed = three.caerus.listResourceHolders(
                ListResourceHoldersOptions.builder().status(ResourceHolderStatus.CONFIRMED).build());

        assertThat(confirmed.holders().stream().map(ResourceHolder::id).collect(Collectors.toList()))
                .containsExactly(three.second.id());
    }

    @Test
    void narrowsByResource() {
        ThreeHolders three = new ThreeHolders();

        assertThat(three.caerus.listResourceHolders(
                ListResourceHoldersOptions.builder().resourceKey("seat_A12").build()).holders()).hasSize(3);
        assertThat(three.caerus.listResourceHolders(
                ListResourceHoldersOptions.builder().resourceKey("otra").build()).holders()).isEmpty();
    }

    @Test
    void pagesAndSaysWhenThereIsMore() {
        ThreeHolders three = new ThreeHolders();

        ResourceHolderPage first = three.caerus.listResourceHolders(ListResourceHoldersOptions.builder().pageSize(2).build());
        assertThat(first.holders()).hasSize(2);
        assertThat(first.hasNextPage()).isTrue();

        ResourceHolderPage second = three.caerus.listResourceHolders(
                ListResourceHoldersOptions.builder().pageSize(2).page(1).build());
        assertThat(second.holders()).hasSize(1);
        assertThat(second.hasNextPage()).isFalse();
    }

    @Test
    void neverSellsTheSameUnitTwiceUnderConcurrency() throws InterruptedException {
        InMemoryCaerusClient caerus = aMock(10);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> taken = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> refused = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < 100; i++) {
            pool.submit(() -> {
                start.await();
                try {
                    taken.add(caerus.pooled("seat_A12").take().id());
                } catch (OutOfStockError e) {
                    refused.add(e);
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(taken).hasSize(10).doesNotHaveDuplicates();
        assertThat(refused).hasSize(90);
        Resource resource = caerus.getResource("seat_A12");
        assertThat(resource.availableAmount()).isZero();
        assertThat(resource.pendingCount()).isEqualTo(10);
    }

    @Test
    void returnsImmutableSnapshots() {
        InMemoryCaerusClient caerus = aMock(3);
        caerus.unitary("seat_A12").take();

        List<ResourceHolder> holders = caerus.snapshot().holders();

        assertThatThrownBy(() -> holders.add(holders.get(0))).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new ArrayList<>(holders)).hasSize(1);
    }
}
