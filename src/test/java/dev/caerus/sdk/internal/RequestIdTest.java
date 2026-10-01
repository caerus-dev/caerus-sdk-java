package dev.caerus.sdk.internal;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;
import dev.caerus.sdk.ErrorReason;
import dev.caerus.sdk.dls.DlsError;
import dev.caerus.sdk.sre.OutOfStockError;
import dev.caerus.sdk.support.GrpcErrors;
import io.grpc.Metadata;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdTest {

    @Test
    void readsTheRequestIdFromTheTrailers() {
        Throwable error = GrpcErrors.withTrailers(Status.Code.NOT_FOUND, "x", GrpcErrors.requestIdTrailers("req_abc-123"));

        assertThat(ErrorDetails.requestIdOf(error)).contains("req_abc-123");
    }

    @Test
    void takesTheFirstWhenTheServerSentSeveral() {
        Metadata trailers = new Metadata();
        trailers.put(ErrorDetails.REQUEST_ID_KEY, "req_first-0001");
        trailers.put(ErrorDetails.REQUEST_ID_KEY, "req_second-002");
        Throwable error = GrpcErrors.withTrailers(Status.Code.NOT_FOUND, "x", trailers);

        assertThat(ErrorDetails.requestIdOf(error)).contains("req_first-0001");
    }

    @Test
    void isEmptyWhenTheTrailersHaveNoRequestId() {
        assertThat(ErrorDetails.requestIdOf(GrpcErrors.status(Status.Code.NOT_FOUND, "x"))).isEmpty();
    }

    @Test
    void isEmptyWhenTheErrorHasNoTrailers() {
        assertThat(ErrorDetails.requestIdOf(new RuntimeException("Simple error"))).isEmpty();
        assertThat(ErrorDetails.requestIdOf(null)).isEmpty();
    }

    @Test
    void enrichesTheMessageWithTheRequestIdAndTheSupportCopy() {
        CaerusError error = new CaerusError("Stock exhausted", ErrorCode.CONFLICT, CaerusErrorOptions.builder()
                .reason(ErrorReason.OUT_OF_STOCK)
                .requestId("req_3fa85f64-5717-4562-b3fc-2c963f66afa6")
                .build());

        assertThat(error.requestId()).contains("req_3fa85f64-5717-4562-b3fc-2c963f66afa6");
        assertThat(error.reason()).contains("OUT_OF_STOCK");
        assertThat(error.getMessage())
                .contains("Stock exhausted")
                .contains("(Request ID: req_3fa85f64-5717-4562-b3fc-2c963f66afa6)")
                .contains("Contacta soporte en Discord con este ID.");
    }

    @Test
    void doesNotRepeatARequestIdTheMessageAlreadyHas() {
        CaerusError error = new CaerusError(
                "Failed (Request ID: req_123). Contacta soporte en Discord con este ID.",
                ErrorCode.UNKNOWN,
                CaerusErrorOptions.builder().requestId("req_123").build());

        Matcher matcher = Pattern.compile("req_123").matcher(error.getMessage());
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        assertThat(count).isEqualTo(1);
    }

    @Test
    void leavesTheMessageAloneWithoutARequestId() {
        assertThat(new CaerusError("plain").getMessage()).isEqualTo("plain");
    }

    @Test
    void pointsAtTheErrorGuide() {
        String expected = "https://github.com/caerus-dev/caerus-sdk-java/blob/main/docs/errores.md";
        assertThat(new OutOfStockError("Out of stock").docUrl()).isEqualTo(expected);
        assertThat(new CaerusError("Something failed").docUrl()).isEqualTo(expected);
    }

    @Test
    void keepsTheCause() {
        RuntimeException cause = new RuntimeException("underneath");
        CaerusError error = new CaerusError("on top", ErrorCode.UNKNOWN, CaerusErrorOptions.builder().cause(cause).build());

        assertThat(error.getCause()).isSameAs(cause);
    }

    @Test
    void isUncheckedSoOneCatchTakesThemAll() {
        assertThat(RuntimeException.class).isAssignableFrom(CaerusError.class);
        assertThat(CaerusError.class).isAssignableFrom(DlsError.class);
    }
}
