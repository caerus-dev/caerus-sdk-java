package dev.caerus.sdk.sre;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.ErrorCode;
import dev.caerus.sdk.support.FakeEngine;
import dev.caerus.sdk.support.GrpcErrors;
import io.grpc.Metadata;
import io.grpc.Status;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class TransportTest {

    private static final String API_KEY = "no-es-una-clave";

    private static FakeEngine engine;
    private CaerusClient client;

    @BeforeAll
    static void startEngine() {
        engine = FakeEngine.start();
    }

    @AfterAll
    static void stopEngine() {
        engine.close();
    }

    @BeforeEach
    void connect() {
        engine.reset();
        client = clientWith(CaerusClientOptions.builder());
    }

    @AfterEach
    void disconnect() {
        client.close();
    }

    static CaerusClient clientWith(CaerusClientOptions.Builder builder) {
        return new CaerusClient(CaerusClient.resolve(
                builder.endpoint(engine.endpoint()).apiKey(API_KEY).tls(false).build(), name -> null));
    }

    private static Metadata.Key<String> key(String name) {
        return Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER);
    }

    @Test
    void sendsTheApiKeyAsABearerTokenInTheMetadata() {
        client.getResource("seat_A12");

        assertThat(engine.lastMetadata().getAll(key("authorization"))).containsExactly("Bearer " + API_KEY);
    }

    @Test
    void sendsNoEnvironmentOfItsOwn() {
        client.getResource("seat_A12");

        assertThat(engine.lastMetadata().get(key("environment_id"))).isNull();
        assertThat(engine.lastMetadata().get(key("environment"))).isNull();
    }

    @Test
    void sendsARequestIdOnEveryCall() {
        client.getResource("seat_A12");
        String first = engine.lastMetadata().get(key("x-request-id"));
        client.getResource("seat_A12");
        String second = engine.lastMetadata().get(key("x-request-id"));

        assertThat(first).matches("^req_[a-zA-Z0-9_\\-]{8,64}$");
        assertThat(second).matches("^req_[a-zA-Z0-9_\\-]{8,64}$");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void turnsANotFoundFromTheServerIntoResourceNotFoundError() {
        engine.on("getResource", (request, observer) ->
                observer.onError(GrpcErrors.status(Status.Code.NOT_FOUND, "Resource not found: seat_A12")));

        ResourceNotFoundError error = catchThrowableOfType(ResourceNotFoundError.class,
                () -> client.getResource("seat_A12"));

        assertThat(error).isInstanceOf(CaerusError.class);
        assertThat(error.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(error.getMessage()).contains("Resource not found: seat_A12");
        assertThat(error.requestId()).isPresent();
    }

    static Stream<Arguments> statuses() {
        return Stream.of(
                Arguments.of(Status.Code.FAILED_PRECONDITION, ConflictError.class, ErrorCode.CONFLICT),
                Arguments.of(Status.Code.INVALID_ARGUMENT, ValidationError.class, ErrorCode.VALIDATION),
                Arguments.of(Status.Code.UNAUTHENTICATED, AuthenticationError.class, ErrorCode.AUTHENTICATION),
                Arguments.of(Status.Code.DEADLINE_EXCEEDED, TimeoutError.class, ErrorCode.TIMEOUT),
                Arguments.of(Status.Code.NOT_FOUND, ResourceNotFoundError.class, ErrorCode.RESOURCE_NOT_FOUND));
    }

    @ParameterizedTest
    @MethodSource("statuses")
    void mapsEachStatusToTheMatchingError(Status.Code code, Class<? extends CaerusError> expected, ErrorCode expectedCode) {
        engine.on("getResource", (request, observer) -> observer.onError(GrpcErrors.status(code, "from the server")));

        CaerusError error = catchThrowableOfType(CaerusError.class, () -> client.getResource("seat_A12"));

        assertThat(error).isExactlyInstanceOf(expected);
        assertThat(error.code()).isEqualTo(expectedCode);
        assertThat(error.getMessage()).contains("from the server");
        assertThat(error.requestId()).isPresent();
    }

    @Test
    void leavesAnythingElseAsTheBaseError() {
        engine.on("getResource", (request, observer) ->
                observer.onError(GrpcErrors.status(Status.Code.INTERNAL, "Unexpected gRPC error")));

        CaerusError error = catchThrowableOfType(CaerusError.class, () -> client.getResource("seat_A12"));

        assertThat(error).isExactlyInstanceOf(CaerusError.class);
        assertThat(error.code()).isEqualTo(ErrorCode.UNKNOWN);
    }

    @Test
    void givesUpOnACallThatRunsPastItsDeadline() {
        engine.on("getResource", (request, observer) -> {
        });

        try (CaerusClient impatient = clientWith(CaerusClientOptions.builder().timeoutMs(150L))) {
            long started = System.nanoTime();
            TimeoutError error = catchThrowableOfType(TimeoutError.class, () -> impatient.getResource("seat_A12"));
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;

            assertThat(error.code()).isEqualTo(ErrorCode.TIMEOUT);
            assertThat(elapsedMs).isLessThan(5_000);
        }
    }

    @Test
    void rejectsCallsMadeAfterItIsClosed() {
        client.close();

        CaerusError error = catchThrowableOfType(CaerusError.class, () -> client.getResource("seat_A12"));

        assertThat(error.getMessage()).contains("has been closed");
        assertThat(error.code()).isEqualTo(ErrorCode.UNKNOWN);
    }

    @Test
    void closingTwiceIsHarmless() {
        client.close();
        client.close();
    }

    @Test
    void rethrowsTheOriginalErrorOnTheCallingThread() {
        engine.on("getResource", (request, observer) ->
                observer.onError(GrpcErrors.status(Status.Code.NOT_FOUND, "Resource not found: seat_A12")));

        Throwable error = org.assertj.core.api.Assertions.catchThrowable(() -> client.getResource("seat_A12"));

        assertThat(error).isInstanceOf(ResourceNotFoundError.class);
        assertThat(error).isNotInstanceOf(java.util.concurrent.CompletionException.class);
    }
}
