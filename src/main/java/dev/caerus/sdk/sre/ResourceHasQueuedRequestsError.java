package dev.caerus.sdk.sre;

import dev.caerus.sdk.CaerusErrorOptions;

public class ResourceHasQueuedRequestsError extends ConflictError {

    public ResourceHasQueuedRequestsError(String message) {
        super(message);
    }

    public ResourceHasQueuedRequestsError(String message, CaerusErrorOptions options) {
        super(message, options);
    }
}
