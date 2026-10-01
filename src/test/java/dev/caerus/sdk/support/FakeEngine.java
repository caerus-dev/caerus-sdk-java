package dev.caerus.sdk.support;

import com.google.protobuf.Empty;
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
import dev.caerus.sdk.internal.proto.sre.SharedResourceEngineGrpc;
import dev.caerus.sdk.internal.proto.sre.TakeRequest;
import dev.caerus.sdk.internal.proto.sre.UpdateResourceRequest;
import io.grpc.stub.StreamObserver;

public final class FakeEngine extends RecordingServer {

    private FakeEngine() {
    }

    public static FakeEngine start() {
        FakeEngine engine = new FakeEngine();
        engine.start(engine.new Service());
        return engine;
    }

    public static ResourceResponse.Builder aResourceResponse() {
        return ResourceResponse.newBuilder()
                .setResourceId("res-1")
                .setKey("seat_A12")
                .setTemplateId("tpl-1")
                .setAvailableAmount(1)
                .setPendingCount(0);
    }

    public static ResourceHolderResponse.Builder aHolderResponse() {
        return ResourceHolderResponse.newBuilder()
                .setHolderId("hld-1")
                .setResourceId("res-1")
                .setStatus(ResourceHolderResponse.ResourceHolderStatus.PENDING)
                .setAmount(1)
                .setExpiresAt(1_785_164_400L);
    }

    private final class Service extends SharedResourceEngineGrpc.SharedResourceEngineImplBase {

        @Override
        public void createResource(CreateResourceRequest request, StreamObserver<ResourceResponse> observer) {
            dispatch("createResource", request, observer, aResourceResponse().build());
        }

        @Override
        public void updateResource(UpdateResourceRequest request, StreamObserver<ResourceResponse> observer) {
            dispatch("updateResource", request, observer, aResourceResponse().build());
        }

        @Override
        public void deleteResource(DeleteResourceRequest request, StreamObserver<Empty> observer) {
            dispatch("deleteResource", request, observer, Empty.getDefaultInstance());
        }

        @Override
        public void take(TakeRequest request, StreamObserver<ResourceHolderResponse> observer) {
            dispatch("take", request, observer, aHolderResponse().build());
        }

        @Override
        public void confirm(ConfirmRequest request, StreamObserver<ResourceHolderResponse> observer) {
            dispatch("confirm", request, observer, aHolderResponse()
                    .setStatus(ResourceHolderResponse.ResourceHolderStatus.CONFIRMED)
                    .build());
        }

        @Override
        public void release(ReleaseRequest request, StreamObserver<Empty> observer) {
            dispatch("release", request, observer, Empty.getDefaultInstance());
        }

        @Override
        public void extend(ExtendRequest request, StreamObserver<ResourceHolderResponse> observer) {
            dispatch("extend", request, observer, aHolderResponse().build());
        }

        @Override
        public void getResource(GetResourceRequest request, StreamObserver<ResourceResponse> observer) {
            dispatch("getResource", request, observer, aResourceResponse().build());
        }

        @Override
        public void getResourcesByGroupKey(
                GetResourcesByGroupKeyRequest request, StreamObserver<GetResourcesByGroupKeyResponse> observer) {
            dispatch("getResourcesByGroupKey", request, observer, GetResourcesByGroupKeyResponse.newBuilder()
                    .addResources(aResourceResponse())
                    .setNextPage(false)
                    .build());
        }

        @Override
        public void getResourceHolder(GetResourceHolderRequest request, StreamObserver<ResourceHolderResponse> observer) {
            dispatch("getResourceHolder", request, observer, aHolderResponse().build());
        }

        @Override
        public void getResourceHoldersList(
                GetResourceHoldersListRequest request, StreamObserver<GetResourceHoldersListResponse> observer) {
            dispatch("getResourceHoldersList", request, observer, GetResourceHoldersListResponse.newBuilder()
                    .addResourceHolders(aHolderResponse())
                    .setNextPage(false)
                    .build());
        }
    }
}
