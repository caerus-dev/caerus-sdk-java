package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;

public class DlsConflictError extends DlsError {

    public DlsConflictError(String message) {
        super(message, ErrorCode.CONFLICT, CaerusErrorOptions.none());
    }

    public DlsConflictError(String message, CaerusErrorOptions options) {
        super(message, ErrorCode.CONFLICT, options);
    }
}
