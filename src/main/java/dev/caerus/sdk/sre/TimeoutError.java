package dev.caerus.sdk.sre;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;

public class TimeoutError extends CaerusError {

    public TimeoutError(String message) {
        super(message, ErrorCode.TIMEOUT, CaerusErrorOptions.none());
    }

    public TimeoutError(String message, CaerusErrorOptions options) {
        super(message, ErrorCode.TIMEOUT, options);
    }
}
