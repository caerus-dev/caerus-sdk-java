package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;

public class DlsNotFoundError extends DlsError {

    public DlsNotFoundError(String message) {
        super(message, ErrorCode.RESOURCE_NOT_FOUND, CaerusErrorOptions.none());
    }

    public DlsNotFoundError(String message, CaerusErrorOptions options) {
        super(message, ErrorCode.RESOURCE_NOT_FOUND, options);
    }
}
