package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusErrorOptions;

public class LockDeniedError extends DlsConflictError {

    public LockDeniedError(String message) {
        super(message);
    }

    public LockDeniedError(String message, CaerusErrorOptions options) {
        super(message, options);
    }
}
