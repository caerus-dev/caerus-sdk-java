package dev.caerus.sdk.sre;

import dev.caerus.sdk.CaerusErrorOptions;

public class HolderNotActiveError extends ConflictError {

    public HolderNotActiveError(String message) {
        super(message);
    }

    public HolderNotActiveError(String message, CaerusErrorOptions options) {
        super(message, options);
    }
}
