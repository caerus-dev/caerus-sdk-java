package dev.caerus.sdk.sre;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;
import dev.caerus.sdk.ErrorReason;
import dev.caerus.sdk.internal.ErrorDetails;
import io.grpc.Status;

final class SreErrors {

    private SreErrors() {
    }

    static CaerusError toCaerusError(Throwable error, String clientRequestId) {
        if (error instanceof CaerusError caerus) {
            return caerus;
        }

        String message = ErrorDetails.messageOf(error, "Caerus call failed");
        String reason = ErrorDetails.reasonOf(error).orElse(null);
        String requestId = ErrorDetails.requestIdOf(error).orElse(clientRequestId);
        CaerusErrorOptions options = CaerusErrorOptions.builder()
                .cause(error)
                .reason(reason)
                .requestId(requestId)
                .build();

        Status.Code code = ErrorDetails.codeOf(error);
        if (code == null) {
            return new CaerusError(message, ErrorCode.UNKNOWN, options);
        }
        switch (code) {
            case NOT_FOUND:
                return new ResourceNotFoundError(message, options);
            case FAILED_PRECONDITION:
                return conflict(reason, message, options);
            case INVALID_ARGUMENT:
                return new ValidationError(message, options);
            case UNAUTHENTICATED:
                return new AuthenticationError(message, options);
            case DEADLINE_EXCEEDED:
                return new TimeoutError(message, options);
            default:
                return new CaerusError(message, ErrorCode.UNKNOWN, options);
        }
    }

    static CaerusError closed() {
        return new CaerusError("This CaerusClient has been closed");
    }

    static CaerusError interrupted() {
        return new CaerusError("Interrupted while waiting for Caerus");
    }

    static CaerusError unexpected(Throwable error) {
        return toCaerusError(error, null);
    }

    private static ConflictError conflict(String reason, String message, CaerusErrorOptions options) {
        if (reason == null) {
            return new ConflictError(message, options);
        }
        switch (reason) {
            case ErrorReason.OUT_OF_STOCK:
                return new OutOfStockError(message, options);
            case ErrorReason.HOLDER_NOT_ACTIVE:
                return new HolderNotActiveError(message, options);
            case ErrorReason.RESOURCE_HAS_ACTIVE_HOLDS:
                return new ResourceHasActiveHoldsError(message, options);
            case ErrorReason.RESOURCE_HAS_QUEUED_REQUESTS:
                return new ResourceHasQueuedRequestsError(message, options);
            default:
                return new ConflictError(message, options);
        }
    }
}
