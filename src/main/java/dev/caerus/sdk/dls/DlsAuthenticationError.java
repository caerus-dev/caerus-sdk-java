package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;

public class DlsAuthenticationError extends DlsError {

    public DlsAuthenticationError(String message) {
        super(message, ErrorCode.AUTHENTICATION, CaerusErrorOptions.none());
    }

    public DlsAuthenticationError(String message, CaerusErrorOptions options) {
        super(message, ErrorCode.AUTHENTICATION, options);
    }
}
