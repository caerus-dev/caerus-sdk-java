package dev.caerus.sdk.sre;

import dev.caerus.sdk.internal.ClientSettings;
import dev.caerus.sdk.internal.GrpcTransport;
import dev.caerus.sdk.internal.proto.sre.ConfirmRequest;
import dev.caerus.sdk.internal.proto.sre.CreateResourceRequest;
import dev.caerus.sdk.internal.proto.sre.DeleteResourceRequest;
import dev.caerus.sdk.internal.proto.sre.ExtendRequest;
import dev.caerus.sdk.internal.proto.sre.GetResourceHolderRequest;
import dev.caerus.sdk.internal.proto.sre.GetResourceHoldersListRequest;
import dev.caerus.sdk.internal.proto.sre.GetResourceRequest;
import dev.caerus.sdk.internal.proto.sre.GetResourcesByGroupKeyRequest;
import dev.caerus.sdk.internal.proto.sre.ReleaseRequest;
import dev.caerus.sdk.internal.proto.sre.SharedResourceEngineGrpc;
import dev.caerus.sdk.internal.proto.sre.TakeOptionalSettings;
import dev.caerus.sdk.internal.proto.sre.TakeRequest;
import dev.caerus.sdk.internal.proto.sre.UpdateResourceRequest;
import io.grpc.MethodDescriptor;

import java.util.concurrent.CompletableFuture;

final class SreCore {

    private final GrpcTransport transport;

    SreCore(ClientSettings settings) {
        this.transport = new GrpcTransport(settings);
    }

    CompletableFuture<Resource> createUnitary(String templateName, String key, CreateResourceOptions options) {
        return createResource(templateName, key, 1, options);
    }

    CompletableFuture<Resource> createMultiple(
            String templateName, String key, int availableAmount, CreateResourceOptions options) {
        Validation.requirePositive(availableAmount, "availableAmount");
        return createResource(templateName, key, availableAmount, options);
    }

    private CompletableFuture<Resource> createResource(
            String templateName, String key, int availableAmount, CreateResourceOptions options) {
        Validation.requireText(templateName, "templateName");
        Validation.requireText(key, "key");
        CreateResourceOptions resolved = options == null ? CreateResourceOptions.none() : options;

        CreateResourceRequest.Builder request = CreateResourceRequest.newBuilder()
                .setTemplateName(templateName)
                .setKey(key)
                .setAvailableAmount(availableAmount);
        resolved.groupKey().ifPresent(request::setGroupKey);
        SreMapping.encodeMetadata(resolved.metadata()).ifPresent(request::setMetadata);

        return call(SharedResourceEngineGrpc.getCreateResourceMethod(), request.build())
                .thenApply(SreMapping::toResource);
    }

    CompletableFuture<Resource> updateResource(String key, int deltaAmount, UpdateResourceOptions options) {
        Validation.requireText(key, "key");
        UpdateResourceOptions resolved = options == null ? UpdateResourceOptions.none() : options;

        UpdateResourceRequest.Builder request = UpdateResourceRequest.newBuilder()
                .setResourceKey(key)
                .setDeltaAmount(deltaAmount);
        resolved.groupKey().ifPresent(request::setGroupKey);
        SreMapping.encodeMetadata(resolved.metadata()).ifPresent(request::setMetadata);
        resolved.idempotencyKey().ifPresent(request::setIdempotencyKey);

        return call(SharedResourceEngineGrpc.getUpdateResourceMethod(), request.build())
                .thenApply(SreMapping::toResource);
    }

    CompletableFuture<Void> deleteResource(String key) {
        Validation.requireText(key, "key");
        return call(SharedResourceEngineGrpc.getDeleteResourceMethod(),
                DeleteResourceRequest.newBuilder().setKey(key).build())
                .thenApply(ignored -> null);
    }

    CompletableFuture<ResourceHolder> take(String resourceKey, int amount, TakeOptions options) {
        TakeOptions resolved = options == null ? TakeOptions.none() : options;
        resolved.ttlSeconds().ifPresent(ttl -> Validation.requirePositive(ttl, "ttlSeconds"));

        TakeOptionalSettings.Builder settings = TakeOptionalSettings.newBuilder();
        resolved.idempotencyKey().ifPresent(settings::setIdempotencyKey);
        resolved.ttlSeconds().ifPresent(settings::setCustomTtlSeconds);
        SreMapping.encodeMetadata(resolved.metadata()).ifPresent(settings::setMetadata);

        TakeRequest request = TakeRequest.newBuilder()
                .setResourceKey(resourceKey)
                .setAmount(amount)
                .setSettings(settings)
                .build();

        return call(SharedResourceEngineGrpc.getTakeMethod(), request)
                .thenApply(response -> SreMapping.assertUsable(
                        SreMapping.toResourceHolder(response),
                        ResourceHolderStatus.PENDING,
                        ResourceHolderStatus.QUEUED));
    }

    CompletableFuture<ResourceHolder> confirm(String resourceHolderId, ConfirmOptions options) {
        Validation.requireText(resourceHolderId, "resourceHolderId");
        ConfirmOptions resolved = options == null ? ConfirmOptions.none() : options;

        ConfirmRequest.Builder request = ConfirmRequest.newBuilder().setResourceHolderId(resourceHolderId);
        SreMapping.encodeMetadata(resolved.metadata()).ifPresent(request::setMetadataPatch);

        return call(SharedResourceEngineGrpc.getConfirmMethod(), request.build())
                .thenApply(response -> SreMapping.assertUsable(
                        SreMapping.toResourceHolder(response),
                        ResourceHolderStatus.CONFIRMED));
    }

    CompletableFuture<Void> release(String resourceHolderId) {
        Validation.requireText(resourceHolderId, "resourceHolderId");
        return call(SharedResourceEngineGrpc.getReleaseMethod(),
                ReleaseRequest.newBuilder().setResourceHolderId(resourceHolderId).build())
                .thenApply(ignored -> null);
    }

    CompletableFuture<ResourceHolder> extend(String resourceHolderId, int extraMs) {
        Validation.requireText(resourceHolderId, "resourceHolderId");
        Validation.requirePositive(extraMs, "extraMs");

        ExtendRequest request = ExtendRequest.newBuilder()
                .setResourceHolderId(resourceHolderId)
                .setExtraMs(extraMs)
                .build();

        return call(SharedResourceEngineGrpc.getExtendMethod(), request)
                .thenApply(response -> SreMapping.assertUsable(
                        SreMapping.toResourceHolder(response),
                        ResourceHolderStatus.PENDING,
                        ResourceHolderStatus.QUEUED));
    }

    CompletableFuture<ResourceHolder> getResourceHolder(String resourceHolderId) {
        Validation.requireText(resourceHolderId, "resourceHolderId");
        return call(SharedResourceEngineGrpc.getGetResourceHolderMethod(),
                GetResourceHolderRequest.newBuilder().setResourceHolderId(resourceHolderId).build())
                .thenApply(SreMapping::toResourceHolder);
    }

    CompletableFuture<Resource> getResource(String key) {
        Validation.requireText(key, "key");
        return call(SharedResourceEngineGrpc.getGetResourceMethod(),
                GetResourceRequest.newBuilder().setKey(key).build())
                .thenApply(SreMapping::toResource);
    }

    CompletableFuture<ResourcePage> getResourcesByGroup(String groupKey, GetResourcesByGroupOptions options) {
        Validation.requireText(groupKey, "groupKey");
        GetResourcesByGroupOptions resolved = options == null ? GetResourcesByGroupOptions.none() : options;

        GetResourcesByGroupKeyRequest.Builder request = GetResourcesByGroupKeyRequest.newBuilder()
                .setGroupKey(groupKey)
                .setPage(resolved.page().orElse(0));
        resolved.pageSize().ifPresent(request::setPageSize);

        return call(SharedResourceEngineGrpc.getGetResourcesByGroupKeyMethod(), request.build())
                .thenApply(SreMapping::toResourcePage);
    }

    CompletableFuture<ResourceHolderPage> listResourceHolders(ListResourceHoldersOptions options) {
        ListResourceHoldersOptions resolved = options == null ? ListResourceHoldersOptions.none() : options;
        boolean oldestFirst = resolved.sort().orElse(HolderSort.NEWEST_FIRST) == HolderSort.OLDEST_FIRST;

        GetResourceHoldersListRequest.Builder request = GetResourceHoldersListRequest.newBuilder()
                .setPage(resolved.page().orElse(0))
                .setCreatedAtSortDirection(oldestFirst
                        ? GetResourceHoldersListRequest.SortDirection.ASCENDING
                        : GetResourceHoldersListRequest.SortDirection.DESCENDING);
        resolved.resourceKey().ifPresent(request::setResourceKey);
        resolved.pageSize().ifPresent(request::setPageSize);
        resolved.status().map(SreMapping::encodeStatus).ifPresent(request::setStatusFilter);

        return call(SharedResourceEngineGrpc.getGetResourceHoldersListMethod(), request.build())
                .thenApply(SreMapping::toResourceHolderPage);
    }

    void close() {
        transport.close();
    }

    private <Req, Res> CompletableFuture<Res> call(MethodDescriptor<Req, Res> method, Req request) {
        return transport.unary(method, request, SreErrors::toCaerusError, SreErrors::closed);
    }
}
