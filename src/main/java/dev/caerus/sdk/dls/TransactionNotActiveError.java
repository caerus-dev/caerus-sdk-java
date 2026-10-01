package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusErrorOptions;

public class TransactionNotActiveError extends DlsConflictError {

    public TransactionNotActiveError(String message) {
        super(message);
    }

    public TransactionNotActiveError(String message, CaerusErrorOptions options) {
        super(message, options);
    }
}
