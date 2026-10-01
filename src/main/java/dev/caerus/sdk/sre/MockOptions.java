package dev.caerus.sdk.sre;

import java.util.List;
import java.util.Optional;

public final class MockOptions {

    private static final MockOptions NONE = builder().build();

    private final List<MockResourceSeed> resources;
    private final Integer defaultTtlSeconds;
    private final Integer defaultPageSize;

    private MockOptions(Builder builder) {
        this.resources = builder.resources;
        this.defaultTtlSeconds = builder.defaultTtlSeconds;
        this.defaultPageSize = builder.defaultPageSize;
    }

    public static MockOptions none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<List<MockResourceSeed>> resources() {
        return Optional.ofNullable(resources);
    }

    public Optional<Integer> defaultTtlSeconds() {
        return Optional.ofNullable(defaultTtlSeconds);
    }

    public Optional<Integer> defaultPageSize() {
        return Optional.ofNullable(defaultPageSize);
    }

    public static final class Builder {

        private List<MockResourceSeed> resources;
        private Integer defaultTtlSeconds;
        private Integer defaultPageSize;

        private Builder() {
        }

        public Builder resources(List<MockResourceSeed> resources) {
            this.resources = resources;
            return this;
        }

        public Builder defaultTtlSeconds(Integer defaultTtlSeconds) {
            this.defaultTtlSeconds = defaultTtlSeconds;
            return this;
        }

        public Builder defaultPageSize(Integer defaultPageSize) {
            this.defaultPageSize = defaultPageSize;
            return this;
        }

        public MockOptions build() {
            return new MockOptions(this);
        }
    }
}
