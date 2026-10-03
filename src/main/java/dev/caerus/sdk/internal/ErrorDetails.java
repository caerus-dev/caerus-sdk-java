package dev.caerus.sdk.internal;

import com.google.protobuf.Any;
import com.google.rpc.ErrorInfo;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;

import java.util.Iterator;
import java.util.Optional;

public final class ErrorDetails {

    public static final Metadata.Key<byte[]> STATUS_DETAILS_KEY =
            Metadata.Key.of("grpc-status-details-bin", Metadata.BINARY_BYTE_MARSHALLER);

    public static final Metadata.Key<String> REQUEST_ID_KEY =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);

    private static final String ERROR_INFO_SUFFIX = "google.rpc.ErrorInfo";

    private ErrorDetails() {
    }

    public static Optional<String> reasonOf(Throwable error) {
        try {
            Metadata trailers = Status.trailersFromThrowable(error);
            if (trailers == null) {
                return Optional.empty();
            }
            byte[] raw = first(trailers.getAll(STATUS_DETAILS_KEY));
            if (raw == null) {
                return Optional.empty();
            }
            return decodeReason(raw);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static Optional<String> decodeReason(byte[] statusBytes) {
        try {
            com.google.rpc.Status status = com.google.rpc.Status.parseFrom(statusBytes);
            for (Any detail : status.getDetailsList()) {
                if (!detail.getTypeUrl().endsWith(ERROR_INFO_SUFFIX)) {
                    continue;
                }
                String reason = ErrorInfo.parseFrom(detail.getValue()).getReason();
                if (!reason.isEmpty()) {
                    return Optional.of(reason);
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static Optional<String> requestIdOf(Throwable error) {
        try {
            Metadata trailers = Status.trailersFromThrowable(error);
            if (trailers == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(first(trailers.getAll(REQUEST_ID_KEY)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static String messageOf(Throwable error, String fallback) {
        if (isGrpc(error)) {
            String description = Status.fromThrowable(error).getDescription();
            if (description != null && !description.isEmpty()) {
                return description;
            }
        }
        String message = error == null ? null : error.getMessage();
        return message == null || message.isEmpty() ? fallback : message;
    }

    public static Status.Code codeOf(Throwable error) {
        return isGrpc(error) ? Status.fromThrowable(error).getCode() : null;
    }

    private static boolean isGrpc(Throwable error) {
        return error instanceof StatusRuntimeException || error instanceof StatusException;
    }

    private static <T> T first(Iterable<T> values) {
        if (values == null) {
            return null;
        }
        Iterator<T> iterator = values.iterator();
        return iterator.hasNext() ? iterator.next() : null;
    }
}
