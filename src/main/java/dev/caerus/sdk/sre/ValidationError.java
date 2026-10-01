package dev.caerus.sdk.sre;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;

public class ValidationError extends CaerusError {

    public ValidationError(String message) {
        super(message, ErrorCode.VALIDATION, CaerusErrorOptions.none());
    }

    public ValidationError(String message, CaerusErrorOptions options) {
        super(message, ErrorCode.VALIDATION, options);
    }
}
