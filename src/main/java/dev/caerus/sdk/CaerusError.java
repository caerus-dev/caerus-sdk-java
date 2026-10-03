package dev.caerus.sdk;

import java.util.Optional;

public class CaerusError extends RuntimeException {

    private static final String DOC_URL = "https://github.com/caerus-dev/caerus-sdk-java/blob/main/docs/errores.md";

    private final ErrorCode code;
    private final String reason;
    private final String requestId;

    public CaerusError(String message) {
        this(message, ErrorCode.UNKNOWN, CaerusErrorOptions.none());
    }

    public CaerusError(String message, ErrorCode code) {
        this(message, code, CaerusErrorOptions.none());
    }

    public CaerusError(String message, ErrorCode code, CaerusErrorOptions options) {
        super(enrich(message, options), options == null ? null : options.cause().orElse(null));
        CaerusErrorOptions resolved = options == null ? CaerusErrorOptions.none() : options;
        this.code = code == null ? ErrorCode.UNKNOWN : code;
        this.reason = resolved.reason().orElse(null);
        this.requestId = resolved.requestId().orElse(null);
    }

    public ErrorCode code() {
        return code;
    }

    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    public Optional<String> requestId() {
        return Optional.ofNullable(requestId);
    }

    public String docUrl() {
        return DOC_URL;
    }

    private static String enrich(String message, CaerusErrorOptions options) {
        String base = message == null ? "" : message;
        if (options == null) {
            return base;
        }
        String id = options.requestId().orElse(null);
        if (id == null || id.isEmpty() || base.contains(id)) {
            return base;
        }
        return base + " (Request ID: " + id + "). Contacta soporte en Discord con este ID.";
    }
}
