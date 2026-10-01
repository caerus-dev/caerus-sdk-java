package dev.caerus.sdk.sre;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.webhooks.Webhooks;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class InMemoryCaerusClient implements SharedResourceApi {

    private static final int DEFAULT_TTL_SECONDS = 300;
    private static final int DEFAULT_PAGE_SIZE = 25;

    private final Object lock = new Object();
    private final Webhooks webhooks = new Webhooks();
    private final Map<String, StoredResource> resources = new LinkedHashMap<>();
    private final Map<String, StoredHolder> holders = new LinkedHashMap<>();
    private final Map<String, String> idempotency = new HashMap<>();
    private final Map<MockMethod, Deque<CaerusError>> failures = new EnumMap<>(MockMethod.class);
    private final int defaultTtlSeconds;
    private final int defaultPageSize;

    private long nowSeconds = Math.floorDiv(System.currentTimeMillis(), 1000L);
    private long sequence;
    private boolean closed;

    public InMemoryCaerusClient() {
        this(MockOptions.none());
    }

    public InMemoryCaerusClient(MockOptions options) {
        MockOptions resolved = options == null ? MockOptions.none() : options;
        this.defaultTtlSeconds = resolved.defaultTtlSeconds().orElse(DEFAULT_TTL_SECONDS);
        this.defaultPageSize = resolved.defaultPageSize().orElse(DEFAULT_PAGE_SIZE);

        for (MockResourceSeed seed : resolved.resources().orElse(Collections.emptyList())) {
            StoredResource resource = new StoredResource();
            resource.id = nextId("res");
            resource.key = seed.key();
            resource.templateId = seed.templateId().orElse("tpl-mock");
            resource.availableAmount = seed.availableAmount();
            resource.groupKey = seed.groupKey().orElse(null);
            resource.metadata = seed.metadata().orElse(null);
            resource.createdAtMs = nowSeconds * 1000;
            resource.updatedAtMs = nowSeconds * 1000;
            resources.put(seed.key(), resource);
        }
    }

    public Webhooks webhooks() {
        return webhooks;
    }

    public void failNext(MockMethod method, CaerusError error) {
        synchronized (lock) {
            failures.computeIfAbsent(method, ignored -> new ArrayDeque<>()).addLast(error);
        }
    }

    public void clearFailures() {
        synchronized (lock) {
            failures.clear();
        }
    }

    public void advanceTime(long seconds) {
        synchronized (lock) {
            if (seconds < 0) {
                throw new ValidationError("advanceTime needs a number of seconds that is not negative");
            }
            nowSeconds += seconds;
            for (StoredHolder holder : holders.values()) {
                if (holder.status == ResourceHolderStatus.PENDING && holder.expiresAtSeconds <= nowSeconds) {
                    giveBack(holder, ResourceHolderStatus.EXPIRED);
                }
            }
        }
    }

    public void expire(String resourceHolderId) {
        synchronized (lock) {
            StoredHolder holder = requireHolder(resourceHolderId);
            if (holder.status != ResourceHolderStatus.PENDING) {
                throw new HolderNotActiveError(
                        "Holder " + resourceHolderId + " is " + holder.status + " and cannot expire.");
            }
            giveBack(holder, ResourceHolderStatus.EXPIRED);
        }
    }

    public MockSnapshot snapshot() {
        synchronized (lock) {
            return new MockSnapshot(
                    resources.values().stream().map(this::toResource).collect(Collectors.toList()),
                    holders.values().stream().map(this::toHolder).collect(Collectors.toList()));
        }
    }

    @Override
    public Resource createUnitary(String templateName, String key, CreateResourceOptions options) {
        synchronized (lock) {
            guard(MockMethod.CREATE_UNITARY);
            return createResource(templateName, key, 1, options);
        }
    }

    @Override
    public Resource createMultiple(String templateName, String key, int availableAmount, CreateResourceOptions options) {
        synchronized (lock) {
            guard(MockMethod.CREATE_MULTIPLE);
            Validation.requirePositive(availableAmount, "availableAmount");
            return createResource(templateName, key, availableAmount, options);
        }
    }

    private Resource createResource(String templateName, String key, int availableAmount, CreateResourceOptions options) {
        Validation.requireText(templateName, "templateName");
        Validation.requireText(key, "key");
        CreateResourceOptions resolved = options == null ? CreateResourceOptions.none() : options;

        if (resources.containsKey(key)) {
            throw new ConflictError("A resource with key " + key + " already exists.");
        }

        StoredResource resource = new StoredResource();
        resource.id = nextId("res");
        resource.key = key;
        resource.templateId = "tpl-" + templateName;
        resource.availableAmount = availableAmount;
        resource.groupKey = resolved.groupKey().orElse(null);
        resource.metadata = resolved.metadata().orElse(null);
        resource.createdAtMs = nowSeconds * 1000;
        resource.updatedAtMs = nowSeconds * 1000;
        resources.put(key, resource);

        return toResource(resource);
    }

    @Override
    public Resource updateResource(String key, int deltaAmount, UpdateResourceOptions options) {
        synchronized (lock) {
            guard(MockMethod.UPDATE_RESOURCE);
            Validation.requireText(key, "key");
            UpdateResourceOptions resolved = options == null ? UpdateResourceOptions.none() : options;

            StoredResource resource = requireResource(key);
            if ((long) resource.availableAmount + deltaAmount < 0) {
                throw new ConflictError("Available amount cannot be negative for resource: " + key);
            }

            resource.availableAmount += deltaAmount;
            resolved.groupKey().ifPresent(groupKey -> resource.groupKey = groupKey);
            resolved.metadata().ifPresent(metadata -> resource.metadata = metadata);
            resource.updatedAtMs = nowSeconds * 1000;

            return toResource(resource);
        }
    }

    @Override
    public void deleteResource(String key) {
        synchronized (lock) {
            guard(MockMethod.DELETE_RESOURCE);
            Validation.requireText(key, "key");

            StoredResource resource = requireResource(key);
            if (resource.pendingCount > 0) {
                throw new ResourceHasActiveHoldsError("Cannot delete resource with active holds: " + key);
            }

            resources.remove(key);
        }
    }

    @Override
    public UnitaryResource unitary(String key) {
        return Handles.unitary(key, (resourceKey, amount, options) -> {
            synchronized (lock) {
                guard(MockMethod.TAKE);
                return take(resourceKey, amount, options);
            }
        });
    }

    @Override
    public PooledResource pooled(String key) {
        return Handles.pooled(key, (resourceKey, amount, options) -> {
            synchronized (lock) {
                guard(amount == 1 ? MockMethod.TAKE : MockMethod.TAKE_MANY);
                return take(resourceKey, amount, options);
            }
        });
    }

    @Override
    public ResourceHolder confirm(String resourceHolderId, ConfirmOptions options) {
        synchronized (lock) {
            guard(MockMethod.CONFIRM);
            StoredHolder holder = requireHolder(resourceHolderId);
            ConfirmOptions resolved = options == null ? ConfirmOptions.none() : options;

            if (holder.status != ResourceHolderStatus.PENDING) {
                throw new HolderNotActiveError(
                        "Holder " + resourceHolderId + " is " + holder.status + " and cannot be confirmed.");
            }

            StoredResource resource = requireResource(holder.resourceKey);
            resource.pendingCount -= holder.amount;
            holder.status = ResourceHolderStatus.CONFIRMED;
            resolved.metadata().ifPresent(metadata -> holder.metadata = metadata);

            return toHolder(holder);
        }
    }

    @Override
    public void release(String resourceHolderId) {
        synchronized (lock) {
            guard(MockMethod.RELEASE);
            StoredHolder holder = requireHolder(resourceHolderId);

            if (holder.status != ResourceHolderStatus.PENDING) {
                throw new HolderNotActiveError(
                        "Holder " + resourceHolderId + " is " + holder.status + " and cannot be released.");
            }

            giveBack(holder, ResourceHolderStatus.RELEASED);
        }
    }

    @Override
    public ResourceHolder extend(String resourceHolderId, int extraMs) {
        synchronized (lock) {
            guard(MockMethod.EXTEND);
            Validation.requirePositive(extraMs, "extraMs");
            StoredHolder holder = requireHolder(resourceHolderId);

            if (holder.status != ResourceHolderStatus.PENDING) {
                throw new HolderNotActiveError(
                        "Holder " + resourceHolderId + " is " + holder.status + " and cannot be extended.");
            }

            holder.expiresAtSeconds += (extraMs + 999L) / 1000L;
            return toHolder(holder);
        }
    }

    @Override
    public ResourceHolder getResourceHolder(String resourceHolderId) {
        synchronized (lock) {
            guard(MockMethod.GET_RESOURCE_HOLDER);
            return toHolder(requireHolder(resourceHolderId));
        }
    }

    @Override
    public Resource getResource(String key) {
        synchronized (lock) {
            guard(MockMethod.GET_RESOURCE);
            Validation.requireText(key, "key");
            return toResource(requireResource(key));
        }
    }

    @Override
    public ResourcePage getResourcesByGroup(String groupKey, GetResourcesByGroupOptions options) {
        synchronized (lock) {
            guard(MockMethod.GET_RESOURCES_BY_GROUP);
            Validation.requireText(groupKey, "groupKey");
            GetResourcesByGroupOptions resolved = options == null ? GetResourcesByGroupOptions.none() : options;

            int page = resolved.page().orElse(0);
            int pageSize = resolved.pageSize().orElse(defaultPageSize);
            List<StoredResource> matching = resources.values().stream()
                    .filter(resource -> groupKey.equals(resource.groupKey))
                    .collect(Collectors.toList());

            return new ResourcePage(
                    slice(matching, page, pageSize).stream().map(this::toResource).collect(Collectors.toList()),
                    hasNextPage(matching.size(), page, pageSize));
        }
    }

    @Override
    public ResourceHolderPage listResourceHolders(ListResourceHoldersOptions options) {
        synchronized (lock) {
            guard(MockMethod.LIST_RESOURCE_HOLDERS);
            ListResourceHoldersOptions resolved = options == null ? ListResourceHoldersOptions.none() : options;

            List<StoredHolder> matching = new ArrayList<>(holders.values());
            resolved.resourceKey().ifPresent(key -> matching.removeIf(held -> !key.equals(held.resourceKey)));
            resolved.status().ifPresent(status -> matching.removeIf(held -> held.status != status));
            if (resolved.sort().orElse(HolderSort.NEWEST_FIRST) != HolderSort.OLDEST_FIRST) {
                Collections.reverse(matching);
            }

            int page = resolved.page().orElse(0);
            int pageSize = resolved.pageSize().orElse(defaultPageSize);

            return new ResourceHolderPage(
                    slice(matching, page, pageSize).stream().map(this::toHolder).collect(Collectors.toList()),
                    hasNextPage(matching.size(), page, pageSize));
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            closed = true;
        }
    }

    private ResourceHolder take(String resourceKey, int amount, TakeOptions options) {
        TakeOptions resolved = options == null ? TakeOptions.none() : options;
        resolved.ttlSeconds().ifPresent(ttl -> Validation.requirePositive(ttl, "ttlSeconds"));

        String idempotencyKey = resolved.idempotencyKey().orElse(null);
        if (idempotencyKey != null) {
            String known = idempotency.get(idempotencyKey);
            if (known != null) {
                StoredHolder replayed = requireHolder(known);
                if (replayed.status != ResourceHolderStatus.PENDING && replayed.status != ResourceHolderStatus.QUEUED) {
                    throw new HolderNotActiveError("Caerus returned holder " + replayed.id + " as " + replayed.status
                            + ", which this call cannot use.");
                }
                return toHolder(replayed);
            }
        }

        StoredResource resource = requireResource(resourceKey);
        if (resource.availableAmount < amount) {
            throw new OutOfStockError("Out of stock for resource: " + resourceKey);
        }

        resource.availableAmount -= amount;
        resource.pendingCount += amount;

        StoredHolder holder = new StoredHolder();
        holder.id = nextId("hld");
        holder.resourceKey = resourceKey;
        holder.resourceId = resource.id;
        holder.amount = amount;
        holder.status = ResourceHolderStatus.PENDING;
        holder.expiresAtSeconds = nowSeconds + resolved.ttlSeconds().orElse(defaultTtlSeconds);
        holder.metadata = resolved.metadata().orElse(null);
        holder.createdAtMs = nowSeconds * 1000;
        holders.put(holder.id, holder);

        if (idempotencyKey != null) {
            idempotency.put(idempotencyKey, holder.id);
        }

        return toHolder(holder);
    }

    private void giveBack(StoredHolder holder, ResourceHolderStatus status) {
        StoredResource resource = requireResource(holder.resourceKey);
        resource.availableAmount += holder.amount;
        resource.pendingCount -= holder.amount;
        holder.status = status;
    }

    private void guard(MockMethod method) {
        if (closed) {
            throw new CaerusError("This InMemoryCaerusClient has been closed");
        }
        Deque<CaerusError> queue = failures.get(method);
        CaerusError failure = queue == null ? null : queue.pollFirst();
        if (failure != null) {
            throw failure;
        }
    }

    private StoredResource requireResource(String key) {
        StoredResource resource = resources.get(key);
        if (resource == null) {
            throw new ResourceNotFoundError("Resource not found: " + key);
        }
        return resource;
    }

    private StoredHolder requireHolder(String id) {
        Validation.requireText(id, "resourceHolderId");
        StoredHolder holder = holders.get(id);
        if (holder == null) {
            throw new ResourceNotFoundError("ResourceHolder not found: " + id);
        }
        return holder;
    }

    private Resource toResource(StoredResource resource) {
        return new Resource(
                resource.id,
                resource.key,
                resource.templateId,
                resource.availableAmount,
                resource.pendingCount,
                Optional.ofNullable(resource.groupKey),
                Optional.ofNullable(resource.metadata),
                Optional.of(Instant.ofEpochMilli(resource.createdAtMs)),
                Optional.of(Instant.ofEpochMilli(resource.updatedAtMs)));
    }

    private ResourceHolder toHolder(StoredHolder holder) {
        return new ResourceHolder(
                holder.id,
                holder.resourceId,
                holder.status,
                holder.amount,
                Instant.ofEpochSecond(holder.expiresAtSeconds),
                Optional.ofNullable(holder.metadata),
                Optional.of(Instant.ofEpochMilli(holder.createdAtMs)));
    }

    private String nextId(String prefix) {
        sequence += 1;
        return prefix + "-" + sequence;
    }

    private static <T> List<T> slice(List<T> items, int page, int pageSize) {
        long start = (long) page * pageSize;
        if (start >= items.size() || start < 0 || pageSize <= 0) {
            return Collections.emptyList();
        }
        long end = Math.min(items.size(), start + pageSize);
        return items.subList((int) start, (int) end);
    }

    private static boolean hasNextPage(int total, int page, int pageSize) {
        return (long) page * pageSize + pageSize < total;
    }

    private static final class StoredResource {
        private String id;
        private String key;
        private String templateId;
        private int availableAmount;
        private int pendingCount;
        private String groupKey;
        private Map<String, Object> metadata;
        private long createdAtMs;
        private long updatedAtMs;
    }

    private static final class StoredHolder {
        private String id;
        private String resourceKey;
        private String resourceId;
        private int amount;
        private ResourceHolderStatus status;
        private long expiresAtSeconds;
        private Map<String, Object> metadata;
        private long createdAtMs;
    }
}
