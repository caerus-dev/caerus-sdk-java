package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusErrorOptions;

public class DeadlockAbortedError extends DlsConflictError {

    public DeadlockAbortedError(String message) {
        super(message);
    }

    public DeadlockAbortedError(String message, CaerusErrorOptions options) {
        super(message, options);
    }
}
