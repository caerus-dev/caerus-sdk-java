package dev.caerus.sdk.sre;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;

public class ResourceNotFoundError extends CaerusError {

    public ResourceNotFoundError(String message) {
        super(message, ErrorCode.RESOURCE_NOT_FOUND, CaerusErrorOptions.none());
    }

    public ResourceNotFoundError(String message, CaerusErrorOptions options) {
        super(message, ErrorCode.RESOURCE_NOT_FOUND, options);
    }
}
