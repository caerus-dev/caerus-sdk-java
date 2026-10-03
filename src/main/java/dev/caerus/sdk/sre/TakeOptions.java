package dev.caerus.sdk.sre;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class TakeOptions {

    private static final TakeOptions NONE = builder().build();

    private final String idempotencyKey;
    private final Integer ttlSeconds;
    private final Map<String, Object> metadata;

    private TakeOptions(Builder builder) {
        this.idempotencyKey = builder.idempotencyKey;
        this.ttlSeconds = builder.ttlSeconds;
        this.metadata = builder.metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
    }

    public static TakeOptions none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<String> idempotencyKey() {
        return Optional.ofNullable(idempotencyKey);
    }

    public Optional<Integer> ttlSeconds() {
        return Optional.ofNullable(ttlSeconds);
    }

    public Optional<Map<String, Object>> metadata() {
        return Optional.ofNullable(metadata);
    }

    public static final class Builder {

        private String idempotencyKey;
        private Integer ttlSeconds;
        private Map<String, Object> metadata;

        private Builder() {
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder ttlSeconds(Integer ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public TakeOptions build() {
            return new TakeOptions(this);
        }
    }
}
