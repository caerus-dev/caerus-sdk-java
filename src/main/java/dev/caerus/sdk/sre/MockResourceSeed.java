package dev.caerus.sdk.sre;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class MockResourceSeed {

    private final String key;
    private final int availableAmount;
    private final String templateId;
    private final String groupKey;
    private final Map<String, Object> metadata;

    private MockResourceSeed(Builder builder) {
        this.key = builder.key;
        this.availableAmount = builder.availableAmount;
        this.templateId = builder.templateId;
        this.groupKey = builder.groupKey;
        this.metadata = builder.metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
    }

    public static MockResourceSeed of(String key, int availableAmount) {
        return builder(key, availableAmount).build();
    }

    public static Builder builder(String key, int availableAmount) {
        return new Builder(key, availableAmount);
    }

    public String key() {
        return key;
    }

    public int availableAmount() {
        return availableAmount;
    }

    public Optional<String> templateId() {
        return Optional.ofNullable(templateId);
    }

    public Optional<String> groupKey() {
        return Optional.ofNullable(groupKey);
    }

    public Optional<Map<String, Object>> metadata() {
        return Optional.ofNullable(metadata);
    }

    public static final class Builder {

        private final String key;
        private final int availableAmount;
        private String templateId;
        private String groupKey;
        private Map<String, Object> metadata;

        private Builder(String key, int availableAmount) {
            this.key = key;
            this.availableAmount = availableAmount;
        }

        public Builder templateId(String templateId) {
            this.templateId = templateId;
            return this;
        }

        public Builder groupKey(String groupKey) {
            this.groupKey = groupKey;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public MockResourceSeed build() {
            return new MockResourceSeed(this);
        }
    }
}
