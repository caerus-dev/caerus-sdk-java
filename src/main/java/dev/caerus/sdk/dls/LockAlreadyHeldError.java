package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusErrorOptions;

public class LockAlreadyHeldError extends DlsConflictError {

    public LockAlreadyHeldError(String message) {
        super(message);
    }

    public LockAlreadyHeldError(String message, CaerusErrorOptions options) {
        super(message, options);
    }
}
