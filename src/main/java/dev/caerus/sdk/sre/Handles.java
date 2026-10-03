package dev.caerus.sdk.sre;

final class Handles {

    @FunctionalInterface
    interface TakeFn {
        ResourceHolder take(String key, int amount, TakeOptions options);
    }

    private Handles() {
    }

    static UnitaryResource unitary(String key, TakeFn take) {
        Validation.requireKey(key);
        return new UnitaryResource() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public ResourceHolder take(TakeOptions options) {
                return take.take(key, 1, options == null ? TakeOptions.none() : options);
            }

            @Override
            public String toString() {
                return "UnitaryResource[" + key + "]";
            }
        };
    }

    static PooledResource pooled(String key, TakeFn take) {
        Validation.requireKey(key);
        return new PooledResource() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public ResourceHolder take(TakeOptions options) {
                return take.take(key, 1, options == null ? TakeOptions.none() : options);
            }

            @Override
            public ResourceHolder takeMany(int amount, TakeOptions options) {
                if (amount <= 0) {
                    throw new ValidationError("amount must be greater than zero");
                }
                return take.take(key, amount, options == null ? TakeOptions.none() : options);
            }

            @Override
            public String toString() {
                return "PooledResource[" + key + "]";
            }
        };
    }
}
