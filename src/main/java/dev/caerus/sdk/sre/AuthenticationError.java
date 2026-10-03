package dev.caerus.sdk.sre;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;

public class AuthenticationError extends CaerusError {

    public AuthenticationError(String message) {
        super(message, ErrorCode.AUTHENTICATION, CaerusErrorOptions.none());
    }

    public AuthenticationError(String message, CaerusErrorOptions options) {
        super(message, ErrorCode.AUTHENTICATION, options);
    }
}
