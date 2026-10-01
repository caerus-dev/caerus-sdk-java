package dev.caerus.sdk.webhooks;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;

public class CaerusWebhookPayloadError extends CaerusError {

    public CaerusWebhookPayloadError(String message) {
        super(message, ErrorCode.VALIDATION, CaerusErrorOptions.none());
    }

    public CaerusWebhookPayloadError(String message, CaerusErrorOptions options) {
        super(message, ErrorCode.VALIDATION, options);
    }
}
