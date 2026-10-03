package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;

public class DlsTimeoutError extends DlsError {

    public DlsTimeoutError(String message) {
        super(message, ErrorCode.TIMEOUT, CaerusErrorOptions.none());
    }

    public DlsTimeoutError(String message, CaerusErrorOptions options) {
        super(message, ErrorCode.TIMEOUT, options);
    }
}
