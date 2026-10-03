package dev.caerus.sdk.sre;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;
import dev.caerus.sdk.internal.Json;
import dev.caerus.sdk.internal.proto.sre.GetResourceHoldersListResponse;
import dev.caerus.sdk.internal.proto.sre.GetResourcesByGroupKeyResponse;
import dev.caerus.sdk.internal.proto.sre.ResourceHolderResponse;
import dev.caerus.sdk.internal.proto.sre.ResourceResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

final class SreMapping {

    private SreMapping() {
    }

    static Optional<String> encodeMetadata(Optional<Map<String, Object>> metadata) {
        return metadata.map(Json::stringify);
    }

    static Optional<Map<String, Object>> decodeMetadata(String raw, String context) {
        if (raw == null || raw.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Json.parseObject(raw));
        } catch (RuntimeException cause) {
            throw new CaerusError(
                    "The metadata stored for " + context + " is not a JSON object, so it cannot be returned as one. "
                            + "Raw value: " + raw,
                    ErrorCode.UNKNOWN,
                    CaerusErrorOptions.builder().cause(cause).build());
        }
    }

    static Instant decodeExpiresAt(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds);
    }

    static Optional<Instant> decodeTimestampMs(long epochMs) {
        return epochMs == 0 ? Optional.empty() : Optional.of(Instant.ofEpochMilli(epochMs));
    }

    static ResourceHolderStatus decodeStatus(int wire, String context) {
        switch (wire) {
            case ResourceHolderResponse.ResourceHolderStatus.PENDING_VALUE:
                return ResourceHolderStatus.PENDING;
            case ResourceHolderResponse.ResourceHolderStatus.CONFIRMED_VALUE:
                return ResourceHolderStatus.CONFIRMED;
            case ResourceHolderResponse.ResourceHolderStatus.RELEASED_VALUE:
                return ResourceHolderStatus.RELEASED;
            case ResourceHolderResponse.ResourceHolderStatus.QUEUED_VALUE:
                return ResourceHolderStatus.QUEUED;
            case ResourceHolderResponse.ResourceHolderStatus.EXPIRED_VALUE:
                return ResourceHolderStatus.EXPIRED;
            default:
                throw new CaerusError("Caerus reported an unknown status (" + wire + ") for " + context
                        + ". Update io.github.caerus-dev:caerus-sdk.");
        }
    }

    static ResourceHolderResponse.ResourceHolderStatus encodeStatus(ResourceHolderStatus status) {
        switch (status) {
            case PENDING:
                return ResourceHolderResponse.ResourceHolderStatus.PENDING;
            case CONFIRMED:
                return ResourceHolderResponse.ResourceHolderStatus.CONFIRMED;
            case RELEASED:
                return ResourceHolderResponse.ResourceHolderStatus.RELEASED;
            case QUEUED:
                return ResourceHolderResponse.ResourceHolderStatus.QUEUED;
            case EXPIRED:
                return ResourceHolderResponse.ResourceHolderStatus.EXPIRED;
            default:
                throw new IllegalArgumentException("Unknown status " + status);
        }
    }

    static ResourceHolder toResourceHolder(ResourceHolderResponse response) {
        String context = "holder " + response.getHolderId();
        return new ResourceHolder(
                response.getHolderId(),
                response.getResourceId(),
                decodeStatus(response.getStatusValue(), context),
                response.getAmount(),
                decodeExpiresAt(response.getExpiresAt()),
                decodeMetadata(response.hasMetadata() ? response.getMetadata() : null, context),
                decodeTimestampMs(response.getCreatedAtMs()));
    }

    static Resource toResource(ResourceResponse response) {
        Optional<String> groupKey = response.hasGroupKey() && !response.getGroupKey().isEmpty()
                ? Optional.of(response.getGroupKey())
                : Optional.empty();
        return new Resource(
                response.getResourceId(),
                response.getKey(),
                response.getTemplateId(),
                response.getAvailableAmount(),
                response.getPendingCount(),
                groupKey,
                decodeMetadata(response.hasMetadata() ? response.getMetadata() : null, "resource " + response.getKey()),
                decodeTimestampMs(response.getCreatedAtMs()),
                decodeTimestampMs(response.getUpdatedAtMs()));
    }

    static ResourcePage toResourcePage(GetResourcesByGroupKeyResponse response) {
        List<Resource> resources = response.getResourcesList().stream()
                .map(SreMapping::toResource)
                .collect(Collectors.toList());
        return new ResourcePage(resources, response.getNextPage());
    }

    static ResourceHolderPage toResourceHolderPage(GetResourceHoldersListResponse response) {
        List<ResourceHolder> holders = response.getResourceHoldersList().stream()
                .map(SreMapping::toResourceHolder)
                .collect(Collectors.toList());
        return new ResourceHolderPage(holders, response.getNextPage());
    }

    static ResourceHolder assertUsable(ResourceHolder holder, ResourceHolderStatus... usable) {
        for (ResourceHolderStatus status : usable) {
            if (holder.status() == status) {
                return holder;
            }
        }
        throw new ConflictError("Caerus returned holder " + holder.id() + " as " + holder.status()
                + ", which this call cannot use.");
    }
}
