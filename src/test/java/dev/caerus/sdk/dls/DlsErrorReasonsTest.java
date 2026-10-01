package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;
import dev.caerus.sdk.internal.ErrorDetails;
import dev.caerus.sdk.support.GrpcErrors;
import dev.caerus.sdk.support.RealTrailers;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static dev.caerus.sdk.support.RealTrailers.grpcError;
import static org.assertj.core.api.Assertions.assertThat;

class DlsErrorReasonsTest {

    @Test
    void aRealDeadlockAbortArrivesTypedAndWithItsReason() {
        DlsError error = DlsErrors.toDlsError(grpcError(Status.Code.ABORTED,
                "Transaction f3d239f6-ab5b-4936-9477-8ac49d32d06c marked ABORT_REQUESTED",
                RealTrailers.DEADLOCK_DETECTED));

        assertThat(error).isInstanceOf(DeadlockAbortedError.class);
        assertThat(error.reason()).contains("DEADLOCK_DETECTED");
        assertThat(error.code()).isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void theCapturedTrailerStillDecodes() {
        assertThat(ErrorDetails.reasonOf(grpcError(Status.Code.ABORTED, "x", RealTrailers.DEADLOCK_DETECTED)))
                .contains("DEADLOCK_DETECTED");
    }

    @Test
    void everyDlsErrorCanBeCaughtAsCaerusError() {
        List<CaerusError> errors = List.of(
                DlsErrors.toDlsError(grpcError(Status.Code.ABORTED, "abortada", RealTrailers.DEADLOCK_DETECTED)),
                DlsErrors.toDlsError(grpcError(Status.Code.NOT_FOUND, "no existe")),
                DlsErrors.toDlsError(grpcError(Status.Code.INVALID_ARGUMENT, "argumento malo")),
                DlsErrors.toDlsError(grpcError(Status.Code.UNAUTHENTICATED, "sin credenciales")),
                DlsErrors.toDlsError(grpcError(Status.Code.DEADLINE_EXCEEDED, "se paso el tiempo")),
                DlsErrors.toDlsError(grpcError(Status.Code.ALREADY_EXISTS, "ya tomado")),
                new LockDeniedError("denegado"));

        for (CaerusError error : errors) {
            assertThat(error).isInstanceOf(DlsError.class);
        }
    }

    @Test
    void anAbortedWithoutReasonFallsIntoTheGenericConflict() {
        DlsError error = DlsErrors.toDlsError(grpcError(Status.Code.ABORTED, "algo pasó"));

        assertThat(error).isExactlyInstanceOf(DlsConflictError.class);
        assertThat(error.reason()).isEmpty();
    }

    @Test
    void anErrorThatIsAlreadyFromTheDlsPassesThroughUnwrapped() {
        DeadlockAbortedError original = new DeadlockAbortedError("victima",
                CaerusErrorOptions.builder().reason("DEADLOCK_DETECTED").build());

        assertThat(DlsErrors.toDlsError(original)).isSameAs(original);
    }

    static Stream<Arguments> reasons() {
        return Stream.of(
                Arguments.of("DEADLOCK_DETECTED", DeadlockAbortedError.class, ErrorCode.CONFLICT),
                Arguments.of("LOCK_DENIED", LockDeniedError.class, ErrorCode.CONFLICT),
                Arguments.of("LOCK_ALREADY_HELD_EXCLUSIVELY", LockAlreadyHeldError.class, ErrorCode.CONFLICT),
                Arguments.of("TRANSACTION_NOT_ACTIVE", TransactionNotActiveError.class, ErrorCode.CONFLICT),
                Arguments.of("LOCK_MODE_MISMATCH", LockModeMismatchError.class, ErrorCode.VALIDATION),
                Arguments.of("LOCK_ACQUISITION_CANCELLED", LockAcquisitionCancelledError.class, ErrorCode.UNKNOWN));
    }

    @ParameterizedTest
    @MethodSource("reasons")
    void theReasonDecidesBeforeTheStatus(String reason, Class<? extends DlsError> expected, ErrorCode code) {
        DlsError error = DlsErrors.toDlsError(GrpcErrors.withReason(Status.Code.INTERNAL, "x", reason));

        assertThat(error).isExactlyInstanceOf(expected);
        assertThat(error.code()).isEqualTo(code);
        assertThat(error.reason()).contains(reason);
    }

    static Stream<Arguments> statuses() {
        return Stream.of(
                Arguments.of(Status.Code.NOT_FOUND, DlsNotFoundError.class, ErrorCode.RESOURCE_NOT_FOUND),
                Arguments.of(Status.Code.ALREADY_EXISTS, LockAlreadyHeldError.class, ErrorCode.CONFLICT),
                Arguments.of(Status.Code.ABORTED, DlsConflictError.class, ErrorCode.CONFLICT),
                Arguments.of(Status.Code.FAILED_PRECONDITION, DlsConflictError.class, ErrorCode.CONFLICT),
                Arguments.of(Status.Code.INVALID_ARGUMENT, DlsValidationError.class, ErrorCode.VALIDATION),
                Arguments.of(Status.Code.UNAUTHENTICATED, DlsAuthenticationError.class, ErrorCode.AUTHENTICATION),
                Arguments.of(Status.Code.DEADLINE_EXCEEDED, DlsTimeoutError.class, ErrorCode.TIMEOUT),
                Arguments.of(Status.Code.CANCELLED, LockAcquisitionCancelledError.class, ErrorCode.UNKNOWN),
                Arguments.of(Status.Code.INTERNAL, DlsError.class, ErrorCode.UNKNOWN),
                Arguments.of(Status.Code.UNAVAILABLE, DlsError.class, ErrorCode.UNKNOWN));
    }

    @ParameterizedTest
    @MethodSource("statuses")
    void withoutAKnownReasonTheStatusDecides(Status.Code status, Class<? extends DlsError> expected, ErrorCode code) {
        DlsError error = DlsErrors.toDlsError(GrpcErrors.withReason(status, "x", "SOMETHING_NEW"));

        assertThat(error).isExactlyInstanceOf(expected);
        assertThat(error.code()).isEqualTo(code);
        assertThat(error.reason()).contains("SOMETHING_NEW");
    }

    @Test
    void propagatesTheServerRequestId() {
        DlsError error = DlsErrors.toDlsError(
                GrpcErrors.withTrailers(Status.Code.FAILED_PRECONDITION, "Lock denied: lock already held",
                        GrpcErrors.requestIdTrailers("req_dls-server-777")),
                "req_dls-client-fallback");

        assertThat(error.requestId()).contains("req_dls-server-777");
        assertThat(error.getMessage()).contains("req_dls-server-777");
    }

    @Test
    void fallsBackToTheClientRequestIdWhenTheServerSentNone() {
        DlsError error = DlsErrors.toDlsError(new IllegalStateException("Connection refused"), "req_dls-client-timeout");

        assertThat(error.requestId()).contains("req_dls-client-timeout");
        assertThat(error.getMessage()).contains("req_dls-client-timeout");
    }
}
