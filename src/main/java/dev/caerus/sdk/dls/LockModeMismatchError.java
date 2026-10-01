package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusErrorOptions;

public class LockModeMismatchError extends DlsValidationError {

    public LockModeMismatchError(String message) {
        super(message);
    }

    public LockModeMismatchError(String message, CaerusErrorOptions options) {
        super(message, options);
    }
}
