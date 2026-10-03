package dev.caerus.sdk.sre;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.ErrorCode;
import dev.caerus.sdk.internal.ErrorDetails;
import dev.caerus.sdk.support.GrpcErrors;
import dev.caerus.sdk.support.RealTrailers;
import io.grpc.Metadata;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static dev.caerus.sdk.support.RealTrailers.SRE;
import static dev.caerus.sdk.support.RealTrailers.bytes;
import static dev.caerus.sdk.support.RealTrailers.grpcError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ErrorReasonsTest {

    static Stream<Arguments> realTrailers() {
        return SRE.entrySet().stream().map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
    }

    @ParameterizedTest
    @MethodSource("realTrailers")
    void decodesTheReasonFromARealTrailer(String expected, String payload) {
        assertThat(ErrorDetails.decodeReason(bytes(payload))).contains(expected);
    }

    @Test
    void pullsTheReasonOutOfAGrpcError() {
        assertThat(ErrorDetails.reasonOf(grpcError(Status.Code.FAILED_PRECONDITION, "nope", SRE.get("OUT_OF_STOCK"))))
                .contains("OUT_OF_STOCK");
    }

    @Test
    void saysNothingWhenThereIsNoTrailer() {
        assertThat(ErrorDetails.reasonOf(grpcError(Status.Code.FAILED_PRECONDITION, "nope"))).isEmpty();
    }

    static Stream<Arguments> garbage() {
        byte[] allSet = new byte[64];
        Arrays.fill(allSet, (byte) 0xff);
        return Stream.of(
                Arguments.of("empty", new byte[0]),
                Arguments.of("a single stray byte", new byte[]{0x1a}),
                Arguments.of("a length that runs past the end", new byte[]{0x1a, 0x7f, 0x01}),
                Arguments.of("random noise", new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff}),
                Arguments.of("a truncated real trailer", Arrays.copyOf(bytes(SRE.get("OUT_OF_STOCK")), 20)),
                Arguments.of("every byte set", allSet));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("garbage")
    void theDecoderRefusesToBecomeASecondFailure(String name, byte[] input) {
        assertThatCode(() -> ErrorDetails.decodeReason(input)).doesNotThrowAnyException();
        assertThat(ErrorDetails.decodeReason(input)).isEmpty();
    }

    @Test
    void survivesAnErrorWithNoMetadataAtAll() {
        assertThat(ErrorDetails.reasonOf(Status.FAILED_PRECONDITION.asRuntimeException())).isEmpty();
        assertThat(ErrorDetails.reasonOf(null)).isEmpty();
        assertThat(ErrorDetails.reasonOf(new IllegalStateException("not an error"))).isEmpty();
    }

    @Test
    void ignoresDetailsThatAreNotErrorInfo() {
        Metadata trailers = new Metadata();
        trailers.put(ErrorDetails.STATUS_DETAILS_KEY, com.google.rpc.Status.newBuilder()
                .setCode(9)
                .addDetails(com.google.protobuf.Any.pack(com.google.rpc.DebugInfo.newBuilder().setDetail("x").build()))
                .build()
                .toByteArray());
        assertThat(ErrorDetails.reasonOf(GrpcErrors.withTrailers(Status.Code.FAILED_PRECONDITION, "x", trailers))).isEmpty();
    }

    @Test
    void turnsOutOfStockIntoOutOfStockError() {
        CaerusError error = SreErrors.toCaerusError(grpcError(Status.Code.FAILED_PRECONDITION, "Out of stock", SRE.get("OUT_OF_STOCK")), null);

        assertThat(error).isInstanceOf(OutOfStockError.class);
        assertThat(error.reason()).contains("OUT_OF_STOCK");
    }

    @Test
    void turnsHolderNotActiveIntoHolderNotActiveError() {
        CaerusError error = SreErrors.toCaerusError(grpcError(Status.Code.FAILED_PRECONDITION, "not active", SRE.get("HOLDER_NOT_ACTIVE")), null);

        assertThat(error).isInstanceOf(HolderNotActiveError.class);
        assertThat(error.reason()).contains("HOLDER_NOT_ACTIVE");
    }

    @Test
    void turnsResourceHasActiveHoldsIntoItsOwnClass() {
        CaerusError error = SreErrors.toCaerusError(grpcError(Status.Code.FAILED_PRECONDITION, "busy", SRE.get("RESOURCE_HAS_ACTIVE_HOLDS")), null);

        assertThat(error).isInstanceOf(ResourceHasActiveHoldsError.class);
    }

    @Test
    void turnsResourceHasQueuedRequestsIntoItsOwnClass() {
        CaerusError error = SreErrors.toCaerusError(
                GrpcErrors.withReason(Status.Code.FAILED_PRECONDITION, "queued", "RESOURCE_HAS_QUEUED_REQUESTS"), null);

        assertThat(error).isInstanceOf(ResourceHasQueuedRequestsError.class);
    }

    @Test
    void keepsCatchingEveryOneOfThemAsConflictError() {
        for (String reason : new String[]{"OUT_OF_STOCK", "HOLDER_NOT_ACTIVE", "RESOURCE_HAS_ACTIVE_HOLDS"}) {
            CaerusError error = SreErrors.toCaerusError(grpcError(Status.Code.FAILED_PRECONDITION, "x", SRE.get(reason)), null);
            assertThat(error).isInstanceOf(ConflictError.class);
            assertThat(error.code()).isEqualTo(ErrorCode.CONFLICT);
        }
    }

    @Test
    void fallsBackToAPlainConflictErrorWhenTheReasonIsUnknown() {
        CaerusError error = SreErrors.toCaerusError(
                grpcError(Status.Code.FAILED_PRECONDITION, "something new", SRE.get("OUT_OF_STOCK").substring(0, 12)), null);

        assertThat(error).isExactlyInstanceOf(ConflictError.class);
    }

    @Test
    void fallsBackWhenTheEngineSendsNoReasonAtAll() {
        CaerusError error = SreErrors.toCaerusError(grpcError(Status.Code.FAILED_PRECONDITION, "old engine"), null);

        assertThat(error).isExactlyInstanceOf(ConflictError.class);
        assertThat(error.reason()).isEmpty();
    }

    @Test
    void carriesTheReasonOnStatusesThatHaveNoSubclass() {
        CaerusError error = SreErrors.toCaerusError(grpcError(Status.Code.NOT_FOUND, "no template", SRE.get("TEMPLATE_NOT_FOUND")), null);

        assertThat(error).isInstanceOf(ResourceNotFoundError.class);
        assertThat(error.reason()).contains("TEMPLATE_NOT_FOUND");
    }

    @Test
    void keepsTheMessageTheEngineWrote() {
        CaerusError error = SreErrors.toCaerusError(
                grpcError(Status.Code.FAILED_PRECONDITION, "Out of stock for resource: seat_A12", SRE.get("OUT_OF_STOCK")), null);

        assertThat(error.getMessage()).isEqualTo("Out of stock for resource: seat_A12");
    }

    @Test
    void anErrorThatIsAlreadyACaerusErrorPassesThrough() {
        OutOfStockError original = new OutOfStockError("gone");

        assertThat(SreErrors.toCaerusError(original, "req_whatever-1234")).isSameAs(original);
    }

    @Test
    void propagatesTheServerRequestId() {
        Metadata trailers = GrpcErrors.requestIdTrailers("req_server-trailer-001");
        CaerusError error = SreErrors.toCaerusError(
                GrpcErrors.withTrailers(Status.Code.FAILED_PRECONDITION, "Out of stock for resource: seats", trailers),
                "req_client-fallback-999");

        assertThat(error.requestId()).contains("req_server-trailer-001");
        assertThat(error.getMessage()).contains("req_server-trailer-001");
    }

    @Test
    void fallsBackToTheClientRequestIdWhenTheServerSentNone() {
        CaerusError error = SreErrors.toCaerusError(
                RealTrailers.grpcError(Status.Code.DEADLINE_EXCEEDED, "Deadline exceeded"), "req_client-timeout-123");

        assertThat(error.requestId()).contains("req_client-timeout-123");
        assertThat(error.getMessage()).contains("req_client-timeout-123");
    }

    @Test
    void aNonGrpcFailureIsTheBaseErrorWithItsOwnMessage() {
        CaerusError error = SreErrors.toCaerusError(new IllegalStateException("Connection refused"), null);

        assertThat(error).isExactlyInstanceOf(CaerusError.class);
        assertThat(error.getMessage()).isEqualTo("Connection refused");
    }
}
