package dev.caerus.sdk.sre;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.ErrorCode;
import dev.caerus.sdk.internal.proto.sre.ConfirmRequest;
import dev.caerus.sdk.internal.proto.sre.CreateResourceRequest;
import dev.caerus.sdk.internal.proto.sre.DeleteResourceRequest;
import dev.caerus.sdk.internal.proto.sre.ExtendRequest;
import dev.caerus.sdk.internal.proto.sre.GetResourceHolderRequest;
import dev.caerus.sdk.internal.proto.sre.GetResourceHoldersListRequest;
import dev.caerus.sdk.internal.proto.sre.GetResourceHoldersListResponse;
import dev.caerus.sdk.internal.proto.sre.GetResourceRequest;
import dev.caerus.sdk.internal.proto.sre.GetResourcesByGroupKeyRequest;
import dev.caerus.sdk.internal.proto.sre.GetResourcesByGroupKeyResponse;
import dev.caerus.sdk.internal.proto.sre.ReleaseRequest;
import dev.caerus.sdk.internal.proto.sre.ResourceHolderResponse;
import dev.caerus.sdk.internal.proto.sre.ResourceResponse;
import dev.caerus.sdk.internal.proto.sre.TakeRequest;
import dev.caerus.sdk.internal.proto.sre.UpdateResourceRequest;
import dev.caerus.sdk.support.FakeEngine;
import dev.caerus.sdk.support.GrpcErrors;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static dev.caerus.sdk.support.FakeEngine.aHolderResponse;
import static dev.caerus.sdk.support.FakeEngine.aResourceResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class MethodsTest {

    private static FakeEngine engine;
    private CaerusClient caerus;

    @BeforeAll
    static void startEngine() {
        engine = FakeEngine.start();
    }

    @AfterAll
    static void stopEngine() {
        engine.close();
    }

    @BeforeEach
    void connect() {
        engine.reset();
        caerus = new CaerusClient(CaerusClient.resolve(CaerusClientOptions.builder()
                .endpoint(engine.endpoint())
                .apiKey("no-es-una-clave")
                .tls(false)
                .build(), name -> null));
    }

    @AfterEach
    void disconnect() {
        caerus.close();
    }

    private static void answerTake(ResourceHolderResponse response) {
        engine.<TakeRequest, ResourceHolderResponse>on("take", (request, observer) -> reply(observer, response));
    }

    private static <T> void reply(StreamObserver<T> observer, T response) {
        observer.onNext(response);
        observer.onCompleted();
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }

    @Test
    void takeAsksForExactlyOneUnit() {
        caerus.unitary("seat_A12").take();

        assertThat(engine.lastMethod()).isEqualTo("take");
        TakeRequest request = engine.lastRequest(TakeRequest.class);
        assertThat(request.getResourceKey()).isEqualTo("seat_A12");
        assertThat(request.getAmount()).isEqualTo(1);
    }

    @Test
    void takePassesTheOptionsThrough() {
        caerus.unitary("seat_A12").take(TakeOptions.builder()
                .idempotencyKey("order-99")
                .ttlSeconds(120)
                .metadata(map("orderId", "12345"))
                .build());

        TakeRequest request = engine.lastRequest(TakeRequest.class);
        assertThat(request.getSettings().getIdempotencyKey()).isEqualTo("order-99");
        assertThat(request.getSettings().getCustomTtlSeconds()).isEqualTo(120);
    }

    @Test
    void takeLeavesUnsetOptionsOffTheWire() {
        caerus.unitary("seat_A12").take();

        TakeRequest request = engine.lastRequest(TakeRequest.class);
        assertThat(request.hasSettings()).isTrue();
        assertThat(request.getSettings().hasIdempotencyKey()).isFalse();
        assertThat(request.getSettings().hasCustomTtlSeconds()).isFalse();
        assertThat(request.getSettings().hasMetadata()).isFalse();
    }

    @Test
    void refusesToBuildAHandleWithoutAKey() {
        assertThatThrownBy(() -> caerus.unitary("  ")).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> caerus.pooled("")).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> caerus.pooled(null)).isInstanceOf(ValidationError.class);
    }

    @Test
    void buildingAHandleCallsNothing() {
        caerus.unitary("seat_A12");
        caerus.pooled("general_admission");

        assertThat(engine.lastMethod()).isNull();
    }

    @Test
    void theHandleRemembersItsKey() {
        assertThat(caerus.unitary("seat_A12").key()).isEqualTo("seat_A12");
        assertThat(caerus.pooled("general_admission").key()).isEqualTo("general_admission");
    }

    @Test
    void rejectsANonPositiveTtl() {
        assertThatThrownBy(() -> caerus.unitary("seat_A12").take(TakeOptions.builder().ttlSeconds(0).build()))
                .isInstanceOf(ValidationError.class);
        assertThat(engine.lastMethod()).isNull();
    }

    @Test
    void takeManyAsksForTheAmountItWasGiven() {
        caerus.pooled("general_admission").takeMany(4);

        TakeRequest request = engine.lastRequest(TakeRequest.class);
        assertThat(request.getResourceKey()).isEqualTo("general_admission");
        assertThat(request.getAmount()).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void takeManyRefusesANonPositiveAmount(int amount) {
        assertThatThrownBy(() -> caerus.pooled("general_admission").takeMany(amount))
                .isInstanceOf(ValidationError.class)
                .hasMessage("amount must be greater than zero");
        assertThat(engine.lastMethod()).isNull();
    }

    @Test
    void throwsWhenTheEngineReportsExpired() {
        answerTake(aHolderResponse().setStatus(ResourceHolderResponse.ResourceHolderStatus.EXPIRED).build());

        ConflictError error = catchThrowableOfType(ConflictError.class, () -> caerus.unitary("seat_A12").take());

        assertThat(error.getMessage()).contains("EXPIRED");
    }

    @Test
    void returnsAQueuedHolderWithTheStatusVisible() {
        answerTake(aHolderResponse().setStatus(ResourceHolderResponse.ResourceHolderStatus.QUEUED).build());

        ResourceHolder holder = caerus.unitary("seat_A12").take();

        assertThat(holder.status()).isEqualTo(ResourceHolderStatus.QUEUED);
        assertThat(holder.id()).isEqualTo("hld-1");
    }

    @Test
    void returnsPendingOnTheHappyPath() {
        answerTake(aHolderResponse().build());

        assertThat(caerus.unitary("seat_A12").take().status()).isEqualTo(ResourceHolderStatus.PENDING);
    }

    @Test
    void doesNotThrowWhenAQueryFindsAnExpiredHolder() {
        engine.<GetResourceHolderRequest, ResourceHolderResponse>on("getResourceHolder", (request, observer) ->
                reply(observer, aHolderResponse().setStatus(ResourceHolderResponse.ResourceHolderStatus.EXPIRED).build()));

        assertThat(caerus.getResourceHolder("hld-1").status()).isEqualTo(ResourceHolderStatus.EXPIRED);
    }

    @Test
    void refusesToGuessAtAStatusItDoesNotKnow() {
        answerTake(aHolderResponse().setStatusValue(99).build());

        assertThatThrownBy(() -> caerus.unitary("seat_A12").take())
                .isInstanceOf(CaerusError.class)
                .hasMessageContaining("unknown status");
    }

    @Test
    void metadataGoesOutAsJsonText() {
        caerus.unitary("seat_A12").take(TakeOptions.builder().metadata(map("orderId", "12345", "items", 2)).build());

        assertThat(engine.lastRequest(TakeRequest.class).getSettings().getMetadata())
                .isEqualTo("{\"orderId\":\"12345\",\"items\":2}");
    }

    @Test
    void metadataComesBackAsAnObject() {
        answerTake(aHolderResponse().setMetadata("{\"orderId\":\"12345\",\"items\":2}").build());

        ResourceHolder holder = caerus.unitary("seat_A12").take();

        assertThat(holder.metadata()).contains(map("orderId", "12345", "items", 2L));
    }

    @Test
    void metadataSurvivesARoundTripUnchanged() {
        Map<String, Object> sent = map("orderId", "12345", "nested", map("seat", "A12"), "count", 3L, "ratio", 0.5,
                "flag", true, "nothing", null);
        engine.<TakeRequest, ResourceHolderResponse>on("take", (request, observer) ->
                reply(observer, aHolderResponse().setMetadata(request.getSettings().getMetadata()).build()));

        ResourceHolder holder = caerus.unitary("seat_A12").take(TakeOptions.builder().metadata(sent).build());

        assertThat(holder.metadata()).contains(sent);
    }

    @Test
    void readsAMissingMetadataAsNone() {
        answerTake(aHolderResponse().build());

        assertThat(caerus.unitary("seat_A12").take().metadata()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void readsABlankMetadataAsNone(String raw) {
        answerTake(aHolderResponse().setMetadata(raw).build());

        assertThat(caerus.unitary("seat_A12").take().metadata()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json at all", "[1,2]", "42", "\"text\"", "null"})
    void complainsLoudlyAboutMetadataThatIsNotAJsonObject(String raw) {
        answerTake(aHolderResponse().setMetadata(raw).build());

        assertThatThrownBy(() -> caerus.unitary("seat_A12").take())
                .isInstanceOf(CaerusError.class)
                .hasMessageContaining("not a JSON object")
                .hasMessageContaining(raw);
    }

    @Test
    void sendsNoMetadataWhenNoneWasGiven() {
        caerus.unitary("seat_A12").take();

        assertThat(engine.lastRequest(TakeRequest.class).getSettings().hasMetadata()).isFalse();
    }

    @Test
    void turnsEpochSecondsIntoTheMatchingInstant() {
        long epochSeconds = Instant.parse("2026-08-02T15:00:00Z").getEpochSecond();
        answerTake(aHolderResponse().setExpiresAt(epochSeconds).build());

        assertThat(caerus.unitary("seat_A12").take().expiresAt()).isEqualTo(Instant.parse("2026-08-02T15:00:00Z"));
    }

    @Test
    void doesNotLandIn1970() {
        answerTake(aHolderResponse().setExpiresAt(1_785_164_400L).build());

        int year = caerus.unitary("seat_A12").take().expiresAt().atZone(ZoneOffset.UTC).getYear();

        assertThat(year).isBetween(2021, 2099);
    }

    @Test
    void createResourceSendsWhatTheEngineNeedsAndReturnsAResource() {
        engine.<CreateResourceRequest, ResourceResponse>on("createResource", (request, observer) ->
                reply(observer, aResourceResponse().setKey("seat_A12").setAvailableAmount(5).build()));

        Resource resource = caerus.createMultiple("seat", "seat_A12", 5, CreateResourceOptions.builder()
                .groupKey("row_A")
                .metadata(map("zone", "platea"))
                .build());

        CreateResourceRequest request = engine.lastRequest(CreateResourceRequest.class);
        assertThat(request.getTemplateName()).isEqualTo("seat");
        assertThat(request.getKey()).isEqualTo("seat_A12");
        assertThat(request.getAvailableAmount()).isEqualTo(5);
        assertThat(request.getGroupKey()).isEqualTo("row_A");
        assertThat(request.getMetadata()).isEqualTo("{\"zone\":\"platea\"}");
        assertThat(resource.key()).isEqualTo("seat_A12");
        assertThat(resource.availableAmount()).isEqualTo(5);
        assertThat(resource.templateId()).isEqualTo("tpl-1");
    }

    @Test
    void createUnitaryAsksForExactlyOne() {
        caerus.createUnitary("seat", "seat_A12");

        CreateResourceRequest request = engine.lastRequest(CreateResourceRequest.class);
        assertThat(request.getAvailableAmount()).isEqualTo(1);
        assertThat(request.hasGroupKey()).isFalse();
        assertThat(request.hasMetadata()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -3})
    void createMultipleRefusesANonPositiveAmount(int amount) {
        assertThatThrownBy(() -> caerus.createMultiple("seat", "seat_A12", amount))
                .isInstanceOf(ValidationError.class)
                .hasMessage("availableAmount must be greater than zero");
        assertThat(engine.lastMethod()).isNull();
    }

    @Test
    void createRefusesABlankTemplateOrKey() {
        assertThatThrownBy(() -> caerus.createUnitary(" ", "seat_A12")).hasMessage("templateName is required");
        assertThatThrownBy(() -> caerus.createUnitary("seat", "")).hasMessage("key is required");
        assertThat(engine.lastMethod()).isNull();
    }

    @Test
    void confirmSendsTheMetadataPatchAsJsonText() {
        caerus.confirm("hld-1", ConfirmOptions.builder().metadata(map("paymentId", "pay_9")).build());

        assertThat(engine.lastMethod()).isEqualTo("confirm");
        ConfirmRequest request = engine.lastRequest(ConfirmRequest.class);
        assertThat(request.getResourceHolderId()).isEqualTo("hld-1");
        assertThat(request.getMetadataPatch()).isEqualTo("{\"paymentId\":\"pay_9\"}");
    }

    @Test
    void confirmRefusesAHolderThatDidNotEndConfirmed() {
        engine.<ConfirmRequest, ResourceHolderResponse>on("confirm", (request, observer) ->
                reply(observer, aHolderResponse().setStatus(ResourceHolderResponse.ResourceHolderStatus.RELEASED).build()));

        assertThatThrownBy(() -> caerus.confirm("hld-1"))
                .isInstanceOf(ConflictError.class)
                .hasMessageContaining("RELEASED");
    }

    @Test
    void releaseSendsTheHolder() {
        caerus.release("hld-1");

        assertThat(engine.lastRequest(ReleaseRequest.class).getResourceHolderId()).isEqualTo("hld-1");
    }

    @Test
    void extendSendsTheExtensionInMilliseconds() {
        caerus.extend("hld-1", 300_000);

        ExtendRequest request = engine.lastRequest(ExtendRequest.class);
        assertThat(request.getResourceHolderId()).isEqualTo("hld-1");
        assertThat(request.getExtraMs()).isEqualTo(300_000);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -30})
    void extendRefusesANonPositiveAmountWithoutCalling(int extraMs) {
        AtomicBoolean called = new AtomicBoolean();
        engine.<ExtendRequest, ResourceHolderResponse>on("extend", (request, observer) -> {
            called.set(true);
            reply(observer, aHolderResponse().build());
        });

        assertThatThrownBy(() -> caerus.extend("hld-1", extraMs)).isInstanceOf(ValidationError.class);
        assertThat(called).isFalse();
    }

    @Test
    void readsAResource() {
        engine.<GetResourceRequest, ResourceResponse>on("getResource", (request, observer) ->
                reply(observer, aResourceResponse().setKey("seat_A12").setPendingCount(2).build()));

        Resource resource = caerus.getResource("seat_A12");

        assertThat(engine.lastRequest(GetResourceRequest.class).getKey()).isEqualTo("seat_A12");
        assertThat(resource.pendingCount()).isEqualTo(2);
    }

    @Test
    void readsAPageOfAGroupAndSaysWhetherThereIsAnother() {
        engine.<GetResourcesByGroupKeyRequest, GetResourcesByGroupKeyResponse>on("getResourcesByGroupKey", (request, observer) ->
                reply(observer, GetResourcesByGroupKeyResponse.newBuilder()
                        .addResources(aResourceResponse().setKey("seat_A12"))
                        .addResources(aResourceResponse().setKey("seat_A13"))
                        .setNextPage(true)
                        .build()));

        ResourcePage page = caerus.getResourcesByGroup("row_A",
                GetResourcesByGroupOptions.builder().page(2).pageSize(10).build());

        GetResourcesByGroupKeyRequest request = engine.lastRequest(GetResourcesByGroupKeyRequest.class);
        assertThat(request.getGroupKey()).isEqualTo("row_A");
        assertThat(request.getPage()).isEqualTo(2);
        assertThat(request.getPageSize()).isEqualTo(10);
        assertThat(page.resources().stream().map(Resource::key).collect(Collectors.toList()))
                .containsExactly("seat_A12", "seat_A13");
        assertThat(page.hasNextPage()).isTrue();
    }

    @Test
    void startsAtTheFirstPageWhenNoneIsAskedFor() {
        caerus.getResourcesByGroup("row_A");

        GetResourcesByGroupKeyRequest request = engine.lastRequest(GetResourcesByGroupKeyRequest.class);
        assertThat(request.getPage()).isZero();
        assertThat(request.hasPageSize()).isFalse();
    }

    @Test
    void listsHoldersAndSaysWhetherThereIsAnotherPage() {
        engine.<GetResourceHoldersListRequest, GetResourceHoldersListResponse>on("getResourceHoldersList", (request, observer) ->
                reply(observer, GetResourceHoldersListResponse.newBuilder()
                        .addResourceHolders(aHolderResponse().setHolderId("hld-1"))
                        .addResourceHolders(aHolderResponse().setHolderId("hld-2"))
                        .setNextPage(true)
                        .build()));

        ResourceHolderPage page = caerus.listResourceHolders(ListResourceHoldersOptions.builder()
                .resourceKey("seat_A12")
                .status(ResourceHolderStatus.PENDING)
                .page(1)
                .pageSize(5)
                .build());

        GetResourceHoldersListRequest request = engine.lastRequest(GetResourceHoldersListRequest.class);
        assertThat(request.getResourceKey()).isEqualTo("seat_A12");
        assertThat(request.getPage()).isEqualTo(1);
        assertThat(request.getPageSize()).isEqualTo(5);
        assertThat(request.hasStatusFilter()).isTrue();
        assertThat(request.getStatusFilterValue()).isZero();
        assertThat(page.holders().stream().map(ResourceHolder::id).collect(Collectors.toList()))
                .containsExactly("hld-1", "hld-2");
        assertThat(page.hasNextPage()).isTrue();
    }

    @Test
    void listsNewestFirstUnlessAskedForTheOtherOrder() {
        caerus.listResourceHolders();
        GetResourceHoldersListRequest first = engine.lastRequest(GetResourceHoldersListRequest.class);
        assertThat(first.getPage()).isZero();
        assertThat(first.getCreatedAtSortDirection()).isEqualTo(GetResourceHoldersListRequest.SortDirection.DESCENDING);

        caerus.listResourceHolders(ListResourceHoldersOptions.builder().sort(HolderSort.OLDEST_FIRST).build());
        assertThat(engine.lastRequest(GetResourceHoldersListRequest.class).getCreatedAtSortDirection())
                .isEqualTo(GetResourceHoldersListRequest.SortDirection.ASCENDING);
    }

    @Test
    void leavesTheStatusFilterOutWhenNoneIsAskedFor() {
        caerus.listResourceHolders(ListResourceHoldersOptions.builder().resourceKey("seat_A12").build());

        assertThat(engine.lastRequest(GetResourceHoldersListRequest.class).hasStatusFilter()).isFalse();
    }

    @Test
    void encodesEveryStatusFilterTheWayTheEngineNumbersThem() {
        Map<ResourceHolderStatus, Integer> wire = Map.of(
                ResourceHolderStatus.PENDING, 0,
                ResourceHolderStatus.CONFIRMED, 1,
                ResourceHolderStatus.RELEASED, 2,
                ResourceHolderStatus.QUEUED, 3,
                ResourceHolderStatus.EXPIRED, 4);
        for (Map.Entry<ResourceHolderStatus, Integer> entry : wire.entrySet()) {
            caerus.listResourceHolders(ListResourceHoldersOptions.builder().status(entry.getKey()).build());
            assertThat(engine.lastRequest(GetResourceHoldersListRequest.class).getStatusFilterValue())
                    .as(entry.getKey().name())
                    .isEqualTo(entry.getValue());
        }
    }

    @Test
    void readsAHolder() {
        engine.<GetResourceHolderRequest, ResourceHolderResponse>on("getResourceHolder", (request, observer) ->
                reply(observer, aHolderResponse().setHolderId("hld-7")
                        .setStatus(ResourceHolderResponse.ResourceHolderStatus.CONFIRMED).build()));

        ResourceHolder holder = caerus.getResourceHolder("hld-7");

        assertThat(engine.lastRequest(GetResourceHolderRequest.class).getResourceHolderId()).isEqualTo("hld-7");
        assertThat(holder.status()).isEqualTo(ResourceHolderStatus.CONFIRMED);
    }

    @Test
    void translatesAServerErrorTheSameWayThroughABusinessMethod() {
        engine.on("getResource", (request, observer) ->
                observer.onError(GrpcErrors.status(Status.Code.FAILED_PRECONDITION, "Holder already confirmed")));

        ConflictError error = catchThrowableOfType(ConflictError.class, () -> caerus.getResource("seat_A12"));

        assertThat(error.getMessage()).contains("Holder already confirmed");
        assertThat(error.requestId()).isPresent();
    }

    @Test
    void runningOutOfStockArrivesAsAConflictCarryingTheEngineMessage() {
        engine.on("take", (request, observer) ->
                observer.onError(GrpcErrors.status(Status.Code.FAILED_PRECONDITION, "Out of stock for resource: seat_A12")));

        ConflictError error = catchThrowableOfType(ConflictError.class, () -> caerus.unitary("seat_A12").take());

        assertThat(error.code()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(error.code()).isNotEqualTo(ErrorCode.UNKNOWN);
        assertThat(error.getMessage()).contains("Out of stock for resource: seat_A12");
        assertThat(error.requestId()).isPresent();
    }

    @Test
    void runningOutOfStockReachesTakeManyTheSameWay() {
        engine.on("take", (request, observer) ->
                observer.onError(GrpcErrors.withReason(Status.Code.FAILED_PRECONDITION, "Out of stock", "OUT_OF_STOCK")));

        assertThatThrownBy(() -> caerus.pooled("general_admission").takeMany(4)).isInstanceOf(OutOfStockError.class);
    }

    @Test
    void sendsUpdateResourceCorrectly() {
        engine.<UpdateResourceRequest, ResourceResponse>on("updateResource", (request, observer) ->
                reply(observer, aResourceResponse()
                        .setKey("general_admission")
                        .setAvailableAmount(150)
                        .setGroupKey("main_hall")
                        .setMetadata("")
                        .build()));

        Resource resource = caerus.updateResource("general_admission", 50,
                UpdateResourceOptions.builder().groupKey("main_hall").idempotencyKey("upd-1").build());

        UpdateResourceRequest request = engine.lastRequest(UpdateResourceRequest.class);
        assertThat(request.getResourceKey()).isEqualTo("general_admission");
        assertThat(request.getDeltaAmount()).isEqualTo(50);
        assertThat(request.getGroupKey()).isEqualTo("main_hall");
        assertThat(request.getIdempotencyKey()).isEqualTo("upd-1");
        assertThat(resource.availableAmount()).isEqualTo(150);
        assertThat(resource.groupKey()).contains("main_hall");
        assertThat(resource.metadata()).isEmpty();
    }

    @Test
    void updateResourceAcceptsANegativeDelta() {
        caerus.updateResource("general_admission", -20);

        assertThat(engine.lastRequest(UpdateResourceRequest.class).getDeltaAmount()).isEqualTo(-20);
    }

    @Test
    void sendsDeleteResourceCorrectly() {
        caerus.deleteResource("seat_A12");

        assertThat(engine.lastRequest(DeleteResourceRequest.class).getKey()).isEqualTo("seat_A12");
    }

    @Test
    void anEmptyGroupKeyReadsAsNone() {
        engine.<GetResourceRequest, ResourceResponse>on("getResource", (request, observer) ->
                reply(observer, aResourceResponse().setGroupKey("").build()));

        assertThat(caerus.getResource("seat_A12").groupKey()).isEmpty();
    }

    @Test
    void theQueriesRefuseBlankArgumentsWithoutCalling() {
        assertThatThrownBy(() -> caerus.getResource(" ")).hasMessage("key is required");
        assertThatThrownBy(() -> caerus.getResourceHolder("")).hasMessage("resourceHolderId is required");
        assertThatThrownBy(() -> caerus.getResourcesByGroup(null)).hasMessage("groupKey is required");
        assertThatThrownBy(() -> caerus.confirm(" ")).hasMessage("resourceHolderId is required");
        assertThatThrownBy(() -> caerus.release(null)).hasMessage("resourceHolderId is required");
        assertThatThrownBy(() -> caerus.deleteResource("")).hasMessage("key is required");
        assertThatThrownBy(() -> caerus.updateResource("", 1)).hasMessage("key is required");
        assertThat(engine.lastMethod()).isNull();
    }
}
