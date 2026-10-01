package dev.caerus.sdk.support;

import com.google.protobuf.Any;
import com.google.rpc.ErrorInfo;
import dev.caerus.sdk.internal.ErrorDetails;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

public final class GrpcErrors {

    private GrpcErrors() {
    }

    public static StatusRuntimeException status(Status.Code code, String description) {
        return Status.fromCode(code).withDescription(description).asRuntimeException(new Metadata());
    }

    public static StatusRuntimeException withReason(Status.Code code, String description, String reason) {
        return Status.fromCode(code).withDescription(description).asRuntimeException(reasonTrailers(code, reason));
    }

    public static StatusRuntimeException withTrailers(Status.Code code, String description, Metadata trailers) {
        return Status.fromCode(code).withDescription(description).asRuntimeException(trailers);
    }

    public static Metadata reasonTrailers(Status.Code code, String reason) {
        Metadata trailers = new Metadata();
        trailers.put(ErrorDetails.STATUS_DETAILS_KEY, statusBytes(code, reason));
        return trailers;
    }

    public static Metadata requestIdTrailers(String requestId) {
        Metadata trailers = new Metadata();
        trailers.put(ErrorDetails.REQUEST_ID_KEY, requestId);
        return trailers;
    }

    public static byte[] statusBytes(Status.Code code, String reason) {
        return com.google.rpc.Status.newBuilder()
                .setCode(code.value())
                .setMessage("from the server")
                .addDetails(Any.pack(ErrorInfo.newBuilder()
                        .setReason(reason)
                        .setDomain("caerus.dev")
                        .build()))
                .build()
                .toByteArray();
    }
}
