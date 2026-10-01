package dev.caerus.sdk.support;

import io.grpc.BindableService;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public abstract class RecordingServer implements AutoCloseable {

    @FunctionalInterface
    public interface Handler<Req, Res> {
        void handle(Req request, StreamObserver<Res> observer);
    }

    private final Map<String, Handler<?, ?>> handlers = new ConcurrentHashMap<>();
    private volatile Metadata lastMetadata;
    private volatile Object lastRequest;
    private volatile String lastMethod;
    private Server server;

    protected final void start(BindableService service) {
        ServerInterceptor capture = new ServerInterceptor() {
            @Override
            public <Req, Res> ServerCall.Listener<Req> interceptCall(
                    ServerCall<Req, Res> call, Metadata headers, ServerCallHandler<Req, Res> next) {
                lastMetadata = headers;
                return next.startCall(call, headers);
            }
        };
        try {
            server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                    .addService(ServerInterceptors.intercept(service, capture))
                    .build()
                    .start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public final String endpoint() {
        return "127.0.0.1:" + server.getPort();
    }

    public final Metadata lastMetadata() {
        return lastMetadata;
    }

    public final <T> T lastRequest(Class<T> type) {
        return type.cast(lastRequest);
    }

    public final String lastMethod() {
        return lastMethod;
    }

    public final <Req, Res> void on(String method, Handler<Req, Res> handler) {
        handlers.put(method, handler);
    }

    public final void reset() {
        handlers.clear();
        lastMetadata = null;
        lastRequest = null;
        lastMethod = null;
    }

    @SuppressWarnings("unchecked")
    protected final <Req, Res> void dispatch(String method, Req request, StreamObserver<Res> observer, Res fallback) {
        lastRequest = request;
        lastMethod = method;
        Handler<Req, Res> handler = (Handler<Req, Res>) handlers.get(method);
        if (handler != null) {
            handler.handle(request, observer);
            return;
        }
        observer.onNext(fallback);
        observer.onCompleted();
    }

    @SuppressWarnings("unchecked")
    protected final <Req, Res> boolean dispatchStream(String method, Req request, StreamObserver<Res> observer) {
        lastRequest = request;
        lastMethod = method;
        Handler<Req, Res> handler = (Handler<Req, Res>) handlers.get(method);
        if (handler != null) {
            handler.handle(request, observer);
            return true;
        }
        return false;
    }

    @Override
    public final void close() {
        try {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
