package dev.caerus.sdk.internal;

import io.grpc.CallOptions;
import io.grpc.ChannelCredentials;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptors;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.TlsChannelCredentials;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class GrpcTransport {

    public static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final ManagedChannel channel;
    private final String apiKey;
    private final long timeoutMs;
    private volatile boolean closed;

    public GrpcTransport(ClientSettings settings) {
        ChannelCredentials credentials = settings.tls()
                ? TlsChannelCredentials.create()
                : InsecureChannelCredentials.create();
        this.channel = Grpc.newChannelBuilder(settings.endpoint(), credentials).build();
        this.apiKey = settings.apiKey();
        this.timeoutMs = settings.timeoutMs();
    }

    public static String newRequestId() {
        return "req_" + UUID.randomUUID();
    }

    public boolean isClosed() {
        return closed;
    }

    public long timeoutMs() {
        return timeoutMs;
    }

    public Metadata headers(String requestId) {
        Metadata metadata = new Metadata();
        metadata.put(AUTHORIZATION_KEY, "Bearer " + apiKey);
        if (requestId != null && !requestId.isEmpty()) {
            metadata.put(ErrorDetails.REQUEST_ID_KEY, requestId);
        }
        return metadata;
    }

    public <Req, Res> ClientCall<Req, Res> newCall(
            MethodDescriptor<Req, Res> method, String requestId, long deadlineMs) {
        CallOptions options = CallOptions.DEFAULT.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS);
        return ClientInterceptors
                .intercept(channel, MetadataUtils.newAttachHeadersInterceptor(headers(requestId)))
                .newCall(method, options);
    }

    public <Req, Res> CompletableFuture<Res> unary(
            MethodDescriptor<Req, Res> method,
            Req request,
            BiFunction<Throwable, String, ? extends RuntimeException> mapError,
            Supplier<? extends RuntimeException> closedError) {
        if (closed) {
            return CompletableFuture.failedFuture(closedError.get());
        }

        String requestId = newRequestId();
        ClientCall<Req, Res> call = newCall(method, requestId, timeoutMs);
        CompletableFuture<Res> result = new CompletableFuture<>();

        ClientCalls.asyncUnaryCall(call, request, new StreamObserver<>() {
            private Res value;

            @Override
            public void onNext(Res response) {
                value = response;
            }

            @Override
            public void onError(Throwable error) {
                result.completeExceptionally(mapError.apply(error, requestId));
            }

            @Override
            public void onCompleted() {
                result.complete(value);
            }
        });

        result.whenComplete((ignored, error) -> {
            if (result.isCancelled()) {
                call.cancel("Cancelled by the caller", null);
            }
        });
        return result;
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        channel.shutdown();
    }
}
