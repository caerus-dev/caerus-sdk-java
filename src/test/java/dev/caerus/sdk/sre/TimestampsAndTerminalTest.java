package dev.caerus.sdk.sre;

import dev.caerus.sdk.internal.proto.sre.ConfirmRequest;
import dev.caerus.sdk.internal.proto.sre.ExtendRequest;
import dev.caerus.sdk.internal.proto.sre.GetResourceHolderRequest;
import dev.caerus.sdk.internal.proto.sre.GetResourceRequest;
import dev.caerus.sdk.internal.proto.sre.ResourceHolderResponse;
import dev.caerus.sdk.internal.proto.sre.ResourceHolderResponse.ResourceHolderStatus;
import dev.caerus.sdk.internal.proto.sre.ResourceResponse;
import dev.caerus.sdk.internal.proto.sre.TakeRequest;
import dev.caerus.sdk.support.FakeEngine;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static dev.caerus.sdk.support.FakeEngine.aHolderResponse;
import static dev.caerus.sdk.support.FakeEngine.aResourceResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimestampsAndTerminalTest {

    private static FakeEngine engine;
    private static CaerusClient caerus;

    @BeforeAll
    static void start() {
        engine = FakeEngine.start();
        caerus = new CaerusClient(CaerusClient.resolve(CaerusClientOptions.builder()
                .apiKey("caer_test_key")
                .endpoint(engine.endpoint())
                .tls(false)
                .build(), name -> null));
    }

    @AfterAll
    static void stop() {
        caerus.close();
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

    private static void answer(String method, ResourceHolderResponse response) {
        engine.<Object, ResourceHolderResponse>on(method, (request, observer) -> reply(observer, response));
    }

    @Test
    void readsAResourceCreatedAndUpdatedAtAsInstants() {
        engine.<GetResourceRequest, ResourceResponse>on("getResource", (request, observer) -> reply(observer,
                aResourceResponse().setCreatedAtMs(1_700_000_000_000L).setUpdatedAtMs(1_700_000_060_000L).build()));

        Resource resource = caerus.getResource("seat_A12");

        assertThat(resource.createdAt()).contains(Instant.ofEpochMilli(1_700_000_000_000L));
        assertThat(resource.updatedAt()).contains(Instant.ofEpochMilli(1_700_000_060_000L));
    }

    @Test
    void readsAHolderCreatedAtAsAnInstant() {
        engine.<GetResourceHolderRequest, ResourceHolderResponse>on("getResourceHolder", (request, observer) ->
                reply(observer, aHolderResponse().setCreatedAtMs(1_700_000_000_000L).build()));

        assertThat(caerus.getResourceHolder("hld-1").createdAt()).contains(Instant.ofEpochMilli(1_700_000_000_000L));
    }

    @Test
    void leavesTheTimestampsEmptyWhenTheEngineSendsNone() {
        Resource resource = caerus.getResource("seat_A12");
        ResourceHolder holder = caerus.getResourceHolder("hld-1");

        assertThat(resource.createdAt()).isEmpty();
        assertThat(resource.updatedAt()).isEmpty();
        assertThat(holder.createdAt()).isEmpty();
    }

    @Test
    void neverTurnsAMissingTimestampInto1970() {
        engine.<GetResourceRequest, ResourceResponse>on("getResource", (request, observer) ->
                reply(observer, aResourceResponse().setCreatedAtMs(0).setUpdatedAtMs(0).build()));

        Resource resource = caerus.getResource("seat_A12");

        assertThat(resource.createdAt()).isEmpty();
        assertThat(resource.updatedAt()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = ResourceHolderStatus.class, names = {"RELEASED", "EXPIRED", "CONFIRMED"})
    void takeRefusesAFinishedHolder(ResourceHolderStatus status) {
        engine.<TakeRequest, ResourceHolderResponse>on("take", (request, observer) ->
                reply(observer, aHolderResponse().setStatus(status).build()));

        assertThatThrownBy(() -> caerus.unitary("seat_A12").take())
                .isInstanceOf(ConflictError.class)
                .hasMessageContaining(status.name());
    }

    @ParameterizedTest
    @EnumSource(value = ResourceHolderStatus.class, names = {"PENDING", "QUEUED"})
    void takeAcceptsAnActiveHolder(ResourceHolderStatus status) {
        engine.<TakeRequest, ResourceHolderResponse>on("take", (request, observer) ->
                reply(observer, aHolderResponse().setStatus(status).build()));

        assertThat(caerus.unitary("seat_A12").take().status().name()).isEqualTo(status.name());
    }

    @Test
    void confirmAcceptsTheConfirmedHolderItIsMeantToProduce() {
        engine.<ConfirmRequest, ResourceHolderResponse>on("confirm", (request, observer) ->
                reply(observer, aHolderResponse().setStatus(ResourceHolderStatus.CONFIRMED).build()));

        assertThat(caerus.confirm("hld-1").status()).isEqualTo(dev.caerus.sdk.sre.ResourceHolderStatus.CONFIRMED);
    }

    @Test
    void confirmRefusesAReleasedHolder() {
        engine.<ConfirmRequest, ResourceHolderResponse>on("confirm", (request, observer) ->
                reply(observer, aHolderResponse().setStatus(ResourceHolderStatus.RELEASED).build()));

        assertThatThrownBy(() -> caerus.confirm("hld-1")).isInstanceOf(ConflictError.class);
    }

    @Test
    void extendRefusesAReleasedHolder() {
        engine.<ExtendRequest, ResourceHolderResponse>on("extend", (request, observer) ->
                reply(observer, aHolderResponse().setStatus(ResourceHolderStatus.RELEASED).build()));

        assertThatThrownBy(() -> caerus.extend("hld-1", 60_000)).isInstanceOf(ConflictError.class);
    }

    @Test
    void extendAcceptsAQueuedHolder() {
        answer("extend", aHolderResponse().setStatus(ResourceHolderStatus.QUEUED).build());

        assertThat(caerus.extend("hld-1", 60_000).status()).isEqualTo(dev.caerus.sdk.sre.ResourceHolderStatus.QUEUED);
    }

    @Test
    void aQueryStillAnswersWithTheFinishedHolderInsteadOfThrowing() {
        answer("getResourceHolder", aHolderResponse().setStatus(ResourceHolderStatus.RELEASED).build());

        assertThat(caerus.getResourceHolder("hld-1").status()).isEqualTo(dev.caerus.sdk.sre.ResourceHolderStatus.RELEASED);
    }

    @Test
    void theMockStampsCreatedAtAndUpdatedAtOnAResource() {
        InMemoryCaerusClient mock = new InMemoryCaerusClient();
        Resource created = mock.createUnitary("butaca", "seat_A12");

        assertThat(created.createdAt()).isPresent();
        assertThat(created.updatedAt()).isPresent();
    }

    @Test
    void theMockMovesUpdatedAtWhenTheResourceChanges() {
        InMemoryCaerusClient mock = new InMemoryCaerusClient();
        Resource created = mock.createMultiple("entrada", "general", 10);

        mock.advanceTime(60);
        Resource updated = mock.updateResource("general", 5);

        assertThat(updated.updatedAt().orElseThrow()).isAfter(created.updatedAt().orElseThrow());
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
    }

    @Test
    void theMockStampsCreatedAtOnAHolder() {
        InMemoryCaerusClient mock = new InMemoryCaerusClient();
        mock.createUnitary("butaca", "seat_A12");

        assertThat(mock.unitary("seat_A12").take().createdAt()).isPresent();
    }

    @Test
    void theMockRefusesToReplayAnIdempotencyKeyWhoseHolderWasReleased() {
        InMemoryCaerusClient mock = new InMemoryCaerusClient();
        mock.createUnitary("butaca", "seat_A12");

        ResourceHolder first = mock.unitary("seat_A12").take(TakeOptions.builder().idempotencyKey("k1").build());
        mock.release(first.id());

        assertThatThrownBy(() -> mock.unitary("seat_A12").take(TakeOptions.builder().idempotencyKey("k1").build()))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void theMockStillReplaysAnIdempotencyKeyWhoseHolderIsAlive() {
        InMemoryCaerusClient mock = new InMemoryCaerusClient();
        mock.createUnitary("butaca", "seat_A12");

        ResourceHolder first = mock.unitary("seat_A12").take(TakeOptions.builder().idempotencyKey("k1").build());
        ResourceHolder again = mock.unitary("seat_A12").take(TakeOptions.builder().idempotencyKey("k1").build());

        assertThat(again.id()).isEqualTo(first.id());
    }
}
