package dev.caerus.sdk.dls;

import java.util.Optional;

public final class BeginTransactionOptions {

    private static final BeginTransactionOptions NONE = builder().build();

    private final Long timeoutMs;

    private BeginTransactionOptions(Builder builder) {
        this.timeoutMs = builder.timeoutMs;
    }

    public static BeginTransactionOptions none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Long> timeoutMs() {
        return Optional.ofNullable(timeoutMs);
    }

    public static final class Builder {

        private Long timeoutMs;

        private Builder() {
        }

        public Builder timeoutMs(Long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public BeginTransactionOptions build() {
            return new BeginTransactionOptions(this);
        }
    }
}
