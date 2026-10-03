package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;

public class DlsValidationError extends DlsError {

    public DlsValidationError(String message) {
        super(message, ErrorCode.VALIDATION, CaerusErrorOptions.none());
    }

    public DlsValidationError(String message, CaerusErrorOptions options) {
        super(message, ErrorCode.VALIDATION, options);
    }
}
