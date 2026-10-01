package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;

public class LockAcquisitionCancelledError extends DlsError {

    public LockAcquisitionCancelledError(String message) {
        super(message, ErrorCode.UNKNOWN, CaerusErrorOptions.none());
    }

    public LockAcquisitionCancelledError(String message, CaerusErrorOptions options) {
        super(message, ErrorCode.UNKNOWN, options);
    }
}
