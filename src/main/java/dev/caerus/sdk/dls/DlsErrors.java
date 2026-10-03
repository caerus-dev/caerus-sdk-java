package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;
import dev.caerus.sdk.ErrorReason;
import dev.caerus.sdk.internal.ErrorDetails;
import io.grpc.Status;

final class DlsErrors {

    static final String ABORTED_BY_USER = "AcquireLock aborted by user";

    private DlsErrors() {
    }

    static DlsError toDlsError(Throwable error) {
        return toDlsError(error, null);
    }

    static DlsError toDlsError(Throwable error, String clientRequestId) {
        if (error instanceof DlsError dls) {
            return dls;
        }

        String message = ErrorDetails.messageOf(error, "DLS call failed");
        String reason = ErrorDetails.reasonOf(error).orElse(null);
        String requestId = ErrorDetails.requestIdOf(error).orElse(clientRequestId);
        CaerusErrorOptions options = CaerusErrorOptions.builder()
                .cause(error)
                .reason(reason)
                .requestId(requestId)
                .build();

        DlsError byReason = byReason(reason, message, options);
        if (byReason != null) {
            return byReason;
        }

        Status.Code code = ErrorDetails.codeOf(error);
        if (code == null) {
            return new DlsError(message, ErrorCode.UNKNOWN, options);
        }
        switch (code) {
            case NOT_FOUND:
                return new DlsNotFoundError(message, options);
            case ALREADY_EXISTS:
                return new LockAlreadyHeldError(message, options);
            case ABORTED:
            case FAILED_PRECONDITION:
                return new DlsConflictError(message, options);
            case INVALID_ARGUMENT:
                return new DlsValidationError(message, options);
            case UNAUTHENTICATED:
                return new DlsAuthenticationError(message, options);
            case DEADLINE_EXCEEDED:
                return new DlsTimeoutError(message, options);
            case CANCELLED:
                return new LockAcquisitionCancelledError(message, options);
            default:
                return new DlsError(message, ErrorCode.UNKNOWN, options);
        }
    }

    static DlsError closed() {
        return new DlsError("This DlsClient has been closed", ErrorCode.UNKNOWN);
    }

    static DlsError abortedByUser(String requestId) {
        return toDlsError(new IllegalStateException(ABORTED_BY_USER), requestId);
    }

    static DlsError interrupted() {
        return new DlsError("Interrupted while waiting for Caerus", ErrorCode.UNKNOWN);
    }

    static DlsError unexpected(Throwable error) {
        return toDlsError(error);
    }

    static boolean isGone(Throwable error) {
        return error instanceof DlsNotFoundError
                || error instanceof TransactionNotActiveError
                || error instanceof DeadlockAbortedError;
    }

    private static DlsError byReason(String reason, String message, CaerusErrorOptions options) {
        if (reason == null) {
            return null;
        }
        switch (reason) {
            case ErrorReason.DEADLOCK_DETECTED:
                return new DeadlockAbortedError(message, options);
            case ErrorReason.LOCK_DENIED:
                return new LockDeniedError(message, options);
            case ErrorReason.LOCK_ALREADY_HELD_EXCLUSIVELY:
                return new LockAlreadyHeldError(message, options);
            case ErrorReason.LOCK_MODE_MISMATCH:
                return new LockModeMismatchError(message, options);
            case ErrorReason.TRANSACTION_NOT_ACTIVE:
                return new TransactionNotActiveError(message, options);
            case ErrorReason.LOCK_ACQUISITION_CANCELLED:
                return new LockAcquisitionCancelledError(message, options);
            default:
                return null;
        }
    }
}
