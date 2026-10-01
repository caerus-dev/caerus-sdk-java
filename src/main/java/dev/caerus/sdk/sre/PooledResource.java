package dev.caerus.sdk.sre;

public interface PooledResource {

    String key();

    default ResourceHolder take() {
        return take(TakeOptions.none());
    }

    ResourceHolder take(TakeOptions options);

    default ResourceHolder takeMany(int amount) {
        return takeMany(amount, TakeOptions.none());
    }

    ResourceHolder takeMany(int amount, TakeOptions options);
}
