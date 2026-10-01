package dev.caerus.sdk.sre;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class CreateResourceOptions {

    private static final CreateResourceOptions NONE = builder().build();

    private final String groupKey;
    private final Map<String, Object> metadata;

    private CreateResourceOptions(Builder builder) {
        this.groupKey = builder.groupKey;
        this.metadata = builder.metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
    }

    public static CreateResourceOptions none() {
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

    public static final class Builder {

        private String groupKey;
        private Map<String, Object> metadata;

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

        public CreateResourceOptions build() {
            return new CreateResourceOptions(this);
        }
    }
}
