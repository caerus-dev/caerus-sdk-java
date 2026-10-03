package dev.caerus.sdk.dls;

import dev.caerus.sdk.AbortSignal;

import java.util.Optional;

public final class AcquireLockOptions {

    private static final AcquireLockOptions NONE = builder().build();

    private final String idempotencyKey;
    private final AbortSignal signal;
    private final Long timeoutMs;
    private final Runnable onQueued;

    private AcquireLockOptions(Builder builder) {
        this.idempotencyKey = builder.idempotencyKey;
        this.signal = builder.signal;
        this.timeoutMs = builder.timeoutMs;
        this.onQueued = builder.onQueued;
    }

    public static AcquireLockOptions none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<String> idempotencyKey() {
        return Optional.ofNullable(idempotencyKey);
    }

    public Optional<AbortSignal> signal() {
        return Optional.ofNullable(signal);
    }

    public Optional<Long> timeoutMs() {
        return Optional.ofNullable(timeoutMs);
    }

    public Optional<Runnable> onQueued() {
        return Optional.ofNullable(onQueued);
    }

    public static final class Builder {

        private String idempotencyKey;
        private AbortSignal signal;
        private Long timeoutMs;
        private Runnable onQueued;

        private Builder() {
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder signal(AbortSignal signal) {
            this.signal = signal;
            return this;
        }

        public Builder timeoutMs(Long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder onQueued(Runnable onQueued) {
            this.onQueued = onQueued;
            return this;
        }

        public AcquireLockOptions build() {
            return new AcquireLockOptions(this);
        }
    }
}
