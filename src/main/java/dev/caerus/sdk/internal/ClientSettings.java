package dev.caerus.sdk.internal;

import dev.caerus.sdk.CaerusLogger;
import dev.caerus.sdk.CaerusSdk;

import java.util.Locale;
import java.util.function.UnaryOperator;

public final class ClientSettings {

    private final String endpoint;
    private final String apiKey;
    private final boolean tls;
    private final long timeoutMs;
    private final CaerusLogger logger;

    private ClientSettings(String endpoint, String apiKey, boolean tls, long timeoutMs, CaerusLogger logger) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.tls = tls;
        this.timeoutMs = timeoutMs;
        this.logger = logger;
    }

    public static ClientSettings resolve(
            ClientProfile profile,
            String apiKey,
            String endpoint,
            Boolean tls,
            Long timeoutMs,
            CaerusLogger logger,
            UnaryOperator<String> environment) {
        String key = apiKey == null ? "" : apiKey.trim();
        if (key.isEmpty()) {
            throw new IllegalArgumentException(profile.missingApiKeyMessage());
        }

        String resolvedEndpoint;
        if (endpoint != null) {
            if (endpoint.trim().isEmpty()) {
                throw new IllegalArgumentException("endpoint cannot be blank");
            }
            resolvedEndpoint = endpoint.trim();
        } else {
            String fromEnv = environment.apply(profile.endpointVariable());
            fromEnv = fromEnv == null ? "" : fromEnv.trim();
            resolvedEndpoint = fromEnv.isEmpty() ? CaerusSdk.DEFAULT_ENDPOINT : fromEnv;
        }

        Boolean envTls = readTls(environment.apply(profile.tlsVariable()));

        long resolvedTimeout = timeoutMs == null ? CaerusSdk.DEFAULT_TIMEOUT_MS : timeoutMs;
        if (resolvedTimeout <= 0) {
            throw new IllegalArgumentException("timeoutMs must be a positive number of milliseconds");
        }

        CaerusLogger resolvedLogger = logger == null ? new StderrLogger(profile.loggerPrefix()) : logger;

        boolean resolvedTls = tls != null ? tls : envTls != null ? envTls : true;

        return new ClientSettings(resolvedEndpoint, key, resolvedTls, resolvedTimeout, resolvedLogger);
    }

    public static UnaryOperator<String> systemEnvironment() {
        return System::getenv;
    }

    private static Boolean readTls(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return !(value.equals("false") || value.equals("0"));
    }

    public String endpoint() {
        return endpoint;
    }

    public String apiKey() {
        return apiKey;
    }

    public boolean tls() {
        return tls;
    }

    public long timeoutMs() {
        return timeoutMs;
    }

    public CaerusLogger logger() {
        return logger;
    }

    @Override
    public String toString() {
        return "ClientSettings[endpoint=" + endpoint + ", tls=" + tls + ", timeoutMs=" + timeoutMs + "]";
    }
}
