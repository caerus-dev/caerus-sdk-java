package dev.caerus.sdk.sre;

public interface UnitaryResource {

    String key();

    default ResourceHolder take() {
        return take(TakeOptions.none());
    }

    ResourceHolder take(TakeOptions options);
}
