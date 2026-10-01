package dev.caerus.sdk.sre;

public interface SharedResourceApi extends AutoCloseable {

    default Resource createUnitary(String templateName, String key) {
        return createUnitary(templateName, key, CreateResourceOptions.none());
    }

    Resource createUnitary(String templateName, String key, CreateResourceOptions options);

    default Resource createMultiple(String templateName, String key, int availableAmount) {
        return createMultiple(templateName, key, availableAmount, CreateResourceOptions.none());
    }

    Resource createMultiple(String templateName, String key, int availableAmount, CreateResourceOptions options);

    default Resource updateResource(String key, int deltaAmount) {
        return updateResource(key, deltaAmount, UpdateResourceOptions.none());
    }

    Resource updateResource(String key, int deltaAmount, UpdateResourceOptions options);

    void deleteResource(String key);

    UnitaryResource unitary(String key);

    PooledResource pooled(String key);

    default ResourceHolder confirm(String resourceHolderId) {
        return confirm(resourceHolderId, ConfirmOptions.none());
    }

    ResourceHolder confirm(String resourceHolderId, ConfirmOptions options);

    void release(String resourceHolderId);

    ResourceHolder extend(String resourceHolderId, int extraMs);

    ResourceHolder getResourceHolder(String resourceHolderId);

    Resource getResource(String key);

    default ResourcePage getResourcesByGroup(String groupKey) {
        return getResourcesByGroup(groupKey, GetResourcesByGroupOptions.none());
    }

    ResourcePage getResourcesByGroup(String groupKey, GetResourcesByGroupOptions options);

    default ResourceHolderPage listResourceHolders() {
        return listResourceHolders(ListResourceHoldersOptions.none());
    }

    ResourceHolderPage listResourceHolders(ListResourceHoldersOptions options);

    @Override
    void close();
}
