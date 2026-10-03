package dev.caerus.sdk.webhooks;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;

public class CaerusSignatureError extends CaerusError {

    public CaerusSignatureError(String message) {
        super(message, ErrorCode.VALIDATION, CaerusErrorOptions.none());
    }

    public CaerusSignatureError(String message, CaerusErrorOptions options) {
        super(message, ErrorCode.VALIDATION, options);
    }
}
