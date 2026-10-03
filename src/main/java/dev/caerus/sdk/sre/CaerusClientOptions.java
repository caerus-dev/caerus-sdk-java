package dev.caerus.sdk.sre;

import dev.caerus.sdk.CaerusLogger;

import java.util.Optional;

public final class CaerusClientOptions {

    private final String apiKey;
    private final String endpoint;
    private final Boolean tls;
    private final Long timeoutMs;
    private final CaerusLogger logger;

    private CaerusClientOptions(Builder builder) {
        this.apiKey = builder.apiKey;
        this.endpoint = builder.endpoint;
        this.tls = builder.tls;
        this.timeoutMs = builder.timeoutMs;
        this.logger = builder.logger;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String apiKey() {
        return apiKey;
    }

    public Optional<String> endpoint() {
        return Optional.ofNullable(endpoint);
    }

    public Optional<Boolean> tls() {
        return Optional.ofNullable(tls);
    }

    public Optional<Long> timeoutMs() {
        return Optional.ofNullable(timeoutMs);
    }

    public Optional<CaerusLogger> logger() {
        return Optional.ofNullable(logger);
    }

    @Override
    public String toString() {
        return "CaerusClientOptions[endpoint=" + endpoint + ", tls=" + tls + ", timeoutMs=" + timeoutMs + "]";
    }

    public static final class Builder {

        private String apiKey;
        private String endpoint;
        private Boolean tls;
        private Long timeoutMs;
        private CaerusLogger logger;

        private Builder() {
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder tls(Boolean tls) {
            this.tls = tls;
            return this;
        }

        public Builder timeoutMs(Long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder logger(CaerusLogger logger) {
            this.logger = logger;
            return this;
        }

        @Override
        public String toString() {
            return "CaerusClientOptions.Builder[endpoint=" + endpoint + ", tls=" + tls + ", timeoutMs=" + timeoutMs + "]";
        }

        public CaerusClientOptions build() {
            return new CaerusClientOptions(this);
        }
    }
}
