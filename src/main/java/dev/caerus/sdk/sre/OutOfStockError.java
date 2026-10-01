package dev.caerus.sdk.sre;

import dev.caerus.sdk.CaerusErrorOptions;

public class OutOfStockError extends ConflictError {

    public OutOfStockError(String message) {
        super(message);
    }

    public OutOfStockError(String message, CaerusErrorOptions options) {
        super(message, options);
    }
}
