package dev.caerus.sdk.sre;

import dev.caerus.sdk.CaerusErrorOptions;

public class ResourceHasActiveHoldsError extends ConflictError {

    public ResourceHasActiveHoldsError(String message) {
        super(message);
    }

    public ResourceHasActiveHoldsError(String message, CaerusErrorOptions options) {
        super(message, options);
    }
}
