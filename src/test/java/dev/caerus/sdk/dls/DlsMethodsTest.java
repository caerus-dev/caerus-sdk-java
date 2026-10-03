package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.ErrorCode;
import dev.caerus.sdk.internal.proto.dls.AcquireLockRequest;
import dev.caerus.sdk.internal.proto.dls.AcquireLockResponse;
import dev.caerus.sdk.internal.proto.dls.BeginTransactionRequest;
import dev.caerus.sdk.internal.proto.dls.GetLockStatusRequest;
import dev.caerus.sdk.internal.proto.dls.GetTransactionStatusRequest;
import dev.caerus.sdk.internal.proto.dls.GetTransactionStatusResponse;
import dev.caerus.sdk.internal.proto.dls.ReleaseLockRequest;
import dev.caerus.sdk.internal.proto.dls.ReleaseTransactionLocksRequest;
import dev.caerus.sdk.internal.proto.dls.RenewTransactionRequest;
import dev.caerus.sdk.support.FakeDlsEngine;
import dev.caerus.sdk.support.GrpcErrors;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static dev.caerus.sdk.support.FakeDlsEngine.acquired;
import static dev.caerus.sdk.support.FakeDlsEngine.withStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class DlsMethodsTest {

    private static FakeDlsEngine engine;
    private static DlsClient client;

    @BeforeAll
    static void start() {
        engine = FakeDlsEngine.start();
        client = DlsTestClients.real(engine);
    }

    @AfterAll
    static void stop() {
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

    @Test
    void beginTransactionPassesTimeoutMs() {
        Transaction tx = client.beginTransaction(BeginTransactionOptions.builder().timeoutMs(5000L).build());

        assertThat(tx.transactionId()).isEqualTo("tx-1");
        assertThat(engine.lastMethod()).isEqualTo("beginTransaction");
        BeginTransactionRequest request = engine.lastRequest(BeginTransactionRequest.class);
        assertThat(request.hasTimeoutMs()).isTrue();
        assertThat(request.getTimeoutMs()).isEqualTo(5000);
    }

    @Test
    void beginTransactionWorksWithoutTimeout() {
        assertThat(client.beginTransaction().transactionId()).isEqualTo("tx-1");
        assertThat(engine.lastRequest(BeginTransactionRequest.class).hasTimeoutMs()).isFalse();
    }

    @Test
    void sendsTheApiKeyAndARequestIdOnEveryCall() {
        client.beginTransaction();

        Metadata metadata = engine.lastMetadata();
        assertThat(metadata.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)))
                .isEqualTo("Bearer test-key");
        assertThat(metadata.get(Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER)))
                .matches("^req_[a-zA-Z0-9_\\-]{8,64}$");
    }

    @Test
    void acquireLockMapsTheModeAndIgnoresQueued() {
        engine.<AcquireLockRequest, AcquireLockResponse>on("acquireLock", (request, observer) -> {
            observer.onNext(withStatus(dev.caerus.sdk.internal.proto.dls.LockStatus.QUEUED));
            observer.onNext(acquired("l-1", 5));
            observer.onCompleted();
        });

        LockHolder lock = client.acquireLock("ns1", "k1", "tx-1", LockMode.EXCLUSIVE,
                AcquireLockOptions.builder().idempotencyKey("idem1").build());

        assertThat(lock.lockId()).isEqualTo("l-1");
        assertThat(lock.fencingToken()).hasValue(5);
        assertThat(lock.status()).isEqualTo(LockStatus.ACQUIRED);
        AcquireLockRequest request = engine.lastRequest(AcquireLockRequest.class);
        assertThat(request.getNamespace()).isEqualTo("ns1");
        assertThat(request.getLockKey()).isEqualTo("k1");
        assertThat(request.getTransactionId()).isEqualTo("tx-1");
        assertThat(request.getRequestedModeValue()).isEqualTo(1);
        assertThat(request.getIdempotencyKey()).isEqualTo("idem1");
    }

    @Test
    void acquireLockLeavesTheIdempotencyKeyOutWhenNoneIsGiven() {
        client.acquireLock("ns1", "k1", "tx-1", LockMode.EXCLUSIVE);

        assertThat(engine.lastRequest(AcquireLockRequest.class).hasIdempotencyKey()).isFalse();
    }

    @Test
    void acquireLockThrowsLockDeniedErrorWhenTheEngineDeniesTheLock() {
        engine.<AcquireLockRequest, AcquireLockResponse>on("acquireLock", (request, observer) ->
                reply(observer, withStatus(dev.caerus.sdk.internal.proto.dls.LockStatus.DENIED)));

        assertThatThrownBy(() -> client.acquireLock("ns1", "k1", "tx-1", LockMode.SHARED_READ))
                .isInstanceOf(LockDeniedError.class);
        assertThat(engine.lastRequest(AcquireLockRequest.class).getRequestedModeValue()).isEqualTo(2);
    }

    @Test
    void aDeniedLockCarriesTheReasonAndIsCatchableAsCaerusError() {
        engine.<AcquireLockRequest, AcquireLockResponse>on("acquireLock", (request, observer) ->
                reply(observer, withStatus(dev.caerus.sdk.internal.proto.dls.LockStatus.DENIED)));

        CaerusError error = catchThrowableOfType(CaerusError.class,
                () -> client.acquireLock("ns1", "k1", "tx-1", LockMode.EXCLUSIVE));

        assertThat(error).isInstanceOf(LockDeniedError.class);
        assertThat(error.reason()).contains("LOCK_DENIED");
        assertThat(error.code()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(error.getMessage()).contains("ns1/k1");
    }

    @Test
    void anUnknownStatusInTheStreamIsLoggedAndIgnored() {
        engine.<AcquireLockRequest, AcquireLockResponse>on("acquireLock", (request, observer) -> {
            observer.onNext(withStatus(dev.caerus.sdk.internal.proto.dls.LockStatus.LOCK_STATUS_UNSPECIFIED));
            observer.onCompleted();
        });
        DlsTestClients.Recorder logger = new DlsTestClients.Recorder();
        try (DlsClient logged = DlsTestClients.real(engine, logger)) {
            DlsError error = catchThrowableOfType(DlsError.class,
                    () -> logged.acquireLock("ns1", "k1", "tx-1", LockMode.EXCLUSIVE));

            assertThat(error).isNotInstanceOf(LockDeniedError.class);
            assertThat(error.getMessage()).contains("Stream ended without terminal status");
            assertThat(logger.messages()).contains("Received unspecified lock status in stream");
        }
    }

    @Test
    void anUnknownStatusIsListedAsUnknownNotAsDenied() {
        engine.<GetTransactionStatusRequest, GetTransactionStatusResponse>on("getTransactionStatus", (request, observer) ->
                reply(observer, GetTransactionStatusResponse.newBuilder()
                        .setStatus("ACTIVE")
                        .addLocks(dev.caerus.sdk.internal.proto.dls.TransactionLockInfo.newBuilder()
                                .setNamespace("ns1").setLockKey("k1").setRequestedModeValue(1).setStatusValue(0))
                        .build()));

        assertThat(client.getTransactionStatus("tx-1").locks().get(0).status()).isEqualTo(LockStatus.UNKNOWN);
    }

    @Test
    void aFencingTokenOfZeroComesBackEmptyNeverAsZero() {
        engine.<AcquireLockRequest, AcquireLockResponse>on("acquireLock", (request, observer) ->
                reply(observer, acquired("lock-1", 0)));

        LockHolder lock = client.acquireLock("ns1", "k1", "tx-1", LockMode.EXCLUSIVE);

        assertThat(lock.status()).isEqualTo(LockStatus.ACQUIRED);
        assertThat(lock.fencingToken()).isEqualTo(OptionalLong.empty());
    }

    @Test
    void renewTransactionPassesItsArguments() {
        Transaction renewed = client.renewTransaction("tx-1", 1000);

        assertThat(renewed.transactionId()).isEqualTo("tx-1");
        RenewTransactionRequest request = engine.lastRequest(RenewTransactionRequest.class);
        assertThat(request.getTransactionId()).isEqualTo("tx-1");
        assertThat(request.getExtraMs()).isEqualTo(1000);
    }

    @Test
    void releaseLockPassesItsArguments() {
        client.releaseLock("l-1", "tx-1");

        ReleaseLockRequest request = engine.lastRequest(ReleaseLockRequest.class);
        assertThat(request.getLockId()).isEqualTo("l-1");
        assertThat(request.getTransactionId()).isEqualTo("tx-1");
    }

    @Test
    void releaseTransactionLocksPassesItsArguments() {
        client.releaseTransactionLocks("tx-1");

        assertThat(engine.lastRequest(ReleaseTransactionLocksRequest.class).getTransactionId()).isEqualTo("tx-1");
    }

    @Test
    void getLockStatusMapsTheResponse() {
        LockStatusResponse status = client.getLockStatus("ns1", "k1");

        assertThat(engine.lastRequest(GetLockStatusRequest.class).getNamespace()).isEqualTo("ns1");
        assertThat(status.isHeld()).isTrue();
        assertThat(status.currentMode()).contains(LockMode.EXCLUSIVE);
        assertThat(status.activeHolders()).hasSize(1);
        assertThat(status.activeHolders().get(0).lockId()).isEqualTo("l-1");
        assertThat(status.activeHolders().get(0).expiresAt()).isEqualTo(1000);
        assertThat(status.activeHolders().get(0).fencingToken()).hasValue(5);
    }

    @Test
    void getTransactionStatusMapsTheResponse() {
        engine.<GetTransactionStatusRequest, GetTransactionStatusResponse>on("getTransactionStatus", (request, observer) ->
                reply(observer, GetTransactionStatusResponse.newBuilder()
                        .setStatus("ABORTED")
                        .setAbortReason("timeout")
                        .addLocks(dev.caerus.sdk.internal.proto.dls.TransactionLockInfo.newBuilder()
                                .setNamespace("n1").setLockKey("k1").setRequestedModeValue(1).setStatusValue(1))
                        .setExpiresAt(500)
                        .build()));

        TransactionStatusResponse status = client.getTransactionStatus("tx-1");

        assertThat(status.status()).isEqualTo("ABORTED");
        assertThat(status.abortReason()).contains("timeout");
        assertThat(status.locks()).hasSize(1);
        assertThat(status.locks().get(0).requestedMode()).isEqualTo(LockMode.EXCLUSIVE);
        assertThat(status.locks().get(0).status()).isEqualTo(LockStatus.ACQUIRED);
        assertThat(status.expiresAt()).isEqualTo(500);
    }

    @Test
    void anEmptyAbortReasonReadsAsNone() {
        assertThat(client.getTransactionStatus("tx-1").abortReason()).isEmpty();
    }

    @Test
    void translatesAServerErrorWithItsRequestId() {
        engine.on("renewTransaction", (request, observer) ->
                observer.onError(GrpcErrors.withReason(Status.Code.FAILED_PRECONDITION, "closed", "TRANSACTION_NOT_ACTIVE")));

        TransactionNotActiveError error = catchThrowableOfType(TransactionNotActiveError.class,
                () -> client.renewTransaction("tx-1", 1000));

        assertThat(error.requestId()).isPresent();
        assertThat(error.getMessage()).contains("closed");
    }
}
