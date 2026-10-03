package dev.caerus.sdk.sre;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;

public class ConflictError extends CaerusError {

    public ConflictError(String message) {
        super(message, ErrorCode.CONFLICT, CaerusErrorOptions.none());
    }

    public ConflictError(String message, CaerusErrorOptions options) {
        super(message, ErrorCode.CONFLICT, options);
    }
}
