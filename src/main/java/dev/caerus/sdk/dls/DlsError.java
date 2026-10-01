package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;

public class DlsError extends CaerusError {

    public DlsError(String message) {
        super(message);
    }

    public DlsError(String message, ErrorCode code) {
        super(message, code);
    }

    public DlsError(String message, ErrorCode code, CaerusErrorOptions options) {
        super(message, code, options);
    }
}
