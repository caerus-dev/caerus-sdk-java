package dev.caerus.sdk.support;

import com.google.protobuf.Empty;
import dev.caerus.sdk.internal.proto.dls.AcquireLockRequest;
import dev.caerus.sdk.internal.proto.dls.AcquireLockResponse;
import dev.caerus.sdk.internal.proto.dls.BeginTransactionRequest;
import dev.caerus.sdk.internal.proto.dls.BeginTransactionResponse;
import dev.caerus.sdk.internal.proto.dls.DistributedLockingEngineGrpc;
import dev.caerus.sdk.internal.proto.dls.GetLockStatusRequest;
import dev.caerus.sdk.internal.proto.dls.GetLockStatusResponse;
import dev.caerus.sdk.internal.proto.dls.GetTransactionStatusRequest;
import dev.caerus.sdk.internal.proto.dls.GetTransactionStatusResponse;
import dev.caerus.sdk.internal.proto.dls.LockHolderInfo;
import dev.caerus.sdk.internal.proto.dls.LockMode;
import dev.caerus.sdk.internal.proto.dls.LockStatus;
import dev.caerus.sdk.internal.proto.dls.ReleaseLockRequest;
import dev.caerus.sdk.internal.proto.dls.ReleaseTransactionLocksRequest;
import dev.caerus.sdk.internal.proto.dls.RenewTransactionRequest;
import dev.caerus.sdk.internal.proto.dls.RenewTransactionResponse;
import io.grpc.stub.StreamObserver;

public final class FakeDlsEngine extends RecordingServer {

    private FakeDlsEngine() {
    }

    public static FakeDlsEngine start() {
        FakeDlsEngine engine = new FakeDlsEngine();
        engine.start(engine.new Service());
        return engine;
    }

    public static AcquireLockResponse acquired(String lockId, long fencingToken) {
        return AcquireLockResponse.newBuilder()
                .setLockId(lockId)
                .setFencingToken(fencingToken)
                .setStatus(LockStatus.ACQUIRED)
                .build();
    }

    public static AcquireLockResponse withStatus(LockStatus status) {
        return AcquireLockResponse.newBuilder().setStatus(status).build();
    }

    private final class Service extends DistributedLockingEngineGrpc.DistributedLockingEngineImplBase {

        @Override
        public void beginTransaction(BeginTransactionRequest request, StreamObserver<BeginTransactionResponse> observer) {
            dispatch("beginTransaction", request, observer,
                    BeginTransactionResponse.newBuilder().setTransactionId("tx-1").build());
        }

        @Override
        public void acquireLock(AcquireLockRequest request, StreamObserver<AcquireLockResponse> observer) {
            if (dispatchStream("acquireLock", request, observer)) {
                return;
            }
            observer.onNext(acquired("lock-1", 1234));
            observer.onCompleted();
        }

        @Override
        public void renewTransaction(RenewTransactionRequest request, StreamObserver<RenewTransactionResponse> observer) {
            dispatch("renewTransaction", request, observer, RenewTransactionResponse.newBuilder()
                    .setTransactionId("tx-1")
                    .setNewExpiresAt(1_234_567_890L)
                    .build());
        }

        @Override
        public void releaseLock(ReleaseLockRequest request, StreamObserver<Empty> observer) {
            dispatch("releaseLock", request, observer, Empty.getDefaultInstance());
        }

        @Override
        public void releaseTransactionLocks(ReleaseTransactionLocksRequest request, StreamObserver<Empty> observer) {
            dispatch("releaseTransactionLocks", request, observer, Empty.getDefaultInstance());
        }

        @Override
        public void getLockStatus(GetLockStatusRequest request, StreamObserver<GetLockStatusResponse> observer) {
            dispatch("getLockStatus", request, observer, GetLockStatusResponse.newBuilder()
                    .setIsHeld(true)
                    .setCurrentMode(LockMode.EXCLUSIVE)
                    .addActiveHolders(LockHolderInfo.newBuilder()
                            .setLockId("l-1")
                            .setExpiresAt(1000)
                            .setFencingToken(5))
                    .setPendingQueueSize(0)
                    .build());
        }

        @Override
        public void getTransactionStatus(
                GetTransactionStatusRequest request, StreamObserver<GetTransactionStatusResponse> observer) {
            dispatch("getTransactionStatus", request, observer, GetTransactionStatusResponse.newBuilder()
                    .setStatus("ACTIVE")
                    .setAbortReason("")
                    .setExpiresAt(1000)
                    .build());
        }
    }
}
