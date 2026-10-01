package dev.caerus.sdk.webhooks;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;

public class CaerusWebhookExpiredError extends CaerusError {

    public CaerusWebhookExpiredError(String message) {
        super(message, ErrorCode.VALIDATION, CaerusErrorOptions.none());
    }

    public CaerusWebhookExpiredError(String message, CaerusErrorOptions options) {
        super(message, ErrorCode.VALIDATION, options);
    }
}
