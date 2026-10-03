package dev.caerus.sdk.sre;

import dev.caerus.sdk.internal.ClientProfile;
import dev.caerus.sdk.internal.ClientSettings;
import dev.caerus.sdk.internal.Futures;
import dev.caerus.sdk.webhooks.Webhooks;

import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

public final class CaerusClient implements SharedResourceApi {

    private final SreCore core;
    private final String endpoint;
    private final long timeoutMs;
    private final Webhooks webhooks = new Webhooks();

    public CaerusClient(CaerusClientOptions options) {
        this(resolve(options, ClientSettings.systemEnvironment()));
    }

    CaerusClient(ClientSettings settings) {
        this.endpoint = settings.endpoint();
        this.timeoutMs = settings.timeoutMs();
        this.core = new SreCore(settings);
    }

    static ClientSettings resolve(CaerusClientOptions options, UnaryOperator<String> environment) {
        if (options == null) {
            throw new IllegalArgumentException("CaerusClient requires an options object with an apiKey");
        }
        return ClientSettings.resolve(
                ClientProfile.SRE,
                options.apiKey(),
                options.endpoint().orElse(null),
                options.tls().orElse(null),
                options.timeoutMs().orElse(null),
                options.logger().orElse(null),
                environment);
    }

    public String endpoint() {
        return endpoint;
    }

    public long timeoutMs() {
        return timeoutMs;
    }

    public Webhooks webhooks() {
        return webhooks;
    }

    @Override
    public Resource createUnitary(String templateName, String key, CreateResourceOptions options) {
        return await(core.createUnitary(templateName, key, options));
    }

    @Override
    public Resource createMultiple(String templateName, String key, int availableAmount, CreateResourceOptions options) {
        return await(core.createMultiple(templateName, key, availableAmount, options));
    }

    @Override
    public Resource updateResource(String key, int deltaAmount, UpdateResourceOptions options) {
        return await(core.updateResource(key, deltaAmount, options));
    }

    @Override
    public void deleteResource(String key) {
        await(core.deleteResource(key));
    }

    @Override
    public UnitaryResource unitary(String key) {
        return Handles.unitary(key, (resourceKey, amount, options) -> await(core.take(resourceKey, amount, options)));
    }

    @Override
    public PooledResource pooled(String key) {
        return Handles.pooled(key, (resourceKey, amount, options) -> await(core.take(resourceKey, amount, options)));
    }

    @Override
    public ResourceHolder confirm(String resourceHolderId, ConfirmOptions options) {
        return await(core.confirm(resourceHolderId, options));
    }

    @Override
    public void release(String resourceHolderId) {
        await(core.release(resourceHolderId));
    }

    @Override
    public ResourceHolder extend(String resourceHolderId, int extraMs) {
        return await(core.extend(resourceHolderId, extraMs));
    }

    @Override
    public ResourceHolder getResourceHolder(String resourceHolderId) {
        return await(core.getResourceHolder(resourceHolderId));
    }

    @Override
    public Resource getResource(String key) {
        return await(core.getResource(key));
    }

    @Override
    public ResourcePage getResourcesByGroup(String groupKey, GetResourcesByGroupOptions options) {
        return await(core.getResourcesByGroup(groupKey, options));
    }

    @Override
    public ResourceHolderPage listResourceHolders(ListResourceHoldersOptions options) {
        return await(core.listResourceHolders(options));
    }

    @Override
    public void close() {
        core.close();
    }

    @Override
    public String toString() {
        return "CaerusClient[endpoint=" + endpoint + "]";
    }

    private static <T> T await(CompletableFuture<T> future) {
        return Futures.await(future, SreErrors::interrupted, SreErrors::unexpected);
    }
}
