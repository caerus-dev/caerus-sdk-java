package dev.caerus.sdk.sre;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ConfirmOptions {

    private static final ConfirmOptions NONE = builder().build();

    private final Map<String, Object> metadata;

    private ConfirmOptions(Builder builder) {
        this.metadata = builder.metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
    }

    public static ConfirmOptions none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Map<String, Object>> metadata() {
        return Optional.ofNullable(metadata);
    }

    public static final class Builder {

        private Map<String, Object> metadata;

        private Builder() {
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ConfirmOptions build() {
            return new ConfirmOptions(this);
        }
    }
}
