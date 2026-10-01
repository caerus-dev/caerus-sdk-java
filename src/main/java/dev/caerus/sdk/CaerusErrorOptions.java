package dev.caerus.sdk;

import java.util.Optional;

public final class CaerusErrorOptions {

    private static final CaerusErrorOptions NONE = builder().build();

    private final Throwable cause;
    private final String reason;
    private final String requestId;

    private CaerusErrorOptions(Builder builder) {
        this.cause = builder.cause;
        this.reason = builder.reason;
        this.requestId = builder.requestId;
    }

    public static CaerusErrorOptions none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Throwable> cause() {
        return Optional.ofNullable(cause);
    }

    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    public Optional<String> requestId() {
        return Optional.ofNullable(requestId);
    }

    public static final class Builder {

        private Throwable cause;
        private String reason;
        private String requestId;

        private Builder() {
        }

        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public CaerusErrorOptions build() {
            return new CaerusErrorOptions(this);
        }
    }
}
