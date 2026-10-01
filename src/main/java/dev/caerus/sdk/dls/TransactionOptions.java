package dev.caerus.sdk.dls;

import dev.caerus.sdk.AbortSignal;

import java.util.function.Consumer;
import java.util.Optional;

public final class TransactionOptions {

    private static final TransactionOptions NONE = builder().build();

    private final Long timeoutMs;
    private final Boolean autoRenew;
    private final AbortSignal signal;
    private final Consumer<DlsError> onTransactionLost;

    private TransactionOptions(Builder builder) {
        this.timeoutMs = builder.timeoutMs;
        this.autoRenew = builder.autoRenew;
        this.signal = builder.signal;
        this.onTransactionLost = builder.onTransactionLost;
    }

    public static TransactionOptions none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Long> timeoutMs() {
        return Optional.ofNullable(timeoutMs);
    }

    public Optional<Boolean> autoRenew() {
        return Optional.ofNullable(autoRenew);
    }

    public Optional<AbortSignal> signal() {
        return Optional.ofNullable(signal);
    }

    public Optional<Consumer<DlsError>> onTransactionLost() {
        return Optional.ofNullable(onTransactionLost);
    }

    public static final class Builder {

        private Long timeoutMs;
        private Boolean autoRenew;
        private AbortSignal signal;
        private Consumer<DlsError> onTransactionLost;

        private Builder() {
        }

        public Builder timeoutMs(Long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder autoRenew(Boolean autoRenew) {
            this.autoRenew = autoRenew;
            return this;
        }

        public Builder signal(AbortSignal signal) {
            this.signal = signal;
            return this;
        }

        public Builder onTransactionLost(Consumer<DlsError> onTransactionLost) {
            this.onTransactionLost = onTransactionLost;
            return this;
        }

        public TransactionOptions build() {
            return new TransactionOptions(this);
        }
    }
}
