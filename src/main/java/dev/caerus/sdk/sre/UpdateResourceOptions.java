package dev.caerus.sdk.sre;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class UpdateResourceOptions {

    private static final UpdateResourceOptions NONE = builder().build();

    private final String groupKey;
    private final Map<String, Object> metadata;
    private final String idempotencyKey;

    private UpdateResourceOptions(Builder builder) {
        this.groupKey = builder.groupKey;
        this.metadata = builder.metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
        this.idempotencyKey = builder.idempotencyKey;
    }

    public static UpdateResourceOptions none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<String> groupKey() {
        return Optional.ofNullable(groupKey);
    }

    public Optional<Map<String, Object>> metadata() {
        return Optional.ofNullable(metadata);
    }

    public Optional<String> idempotencyKey() {
        return Optional.ofNullable(idempotencyKey);
    }

    public static final class Builder {

        private String groupKey;
        private Map<String, Object> metadata;
        private String idempotencyKey;

        private Builder() {
        }

        public Builder groupKey(String groupKey) {
            this.groupKey = groupKey;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public UpdateResourceOptions build() {
            return new UpdateResourceOptions(this);
        }
    }
}
