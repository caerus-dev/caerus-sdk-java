package dev.caerus.sdk.internal;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;

public final class Futures {

    private Futures() {
    }

    public static <T> T await(
            CompletableFuture<T> future,
            Supplier<? extends RuntimeException> onInterrupt,
            Function<Throwable, ? extends RuntimeException> onUnexpected) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw onInterrupt.get();
        } catch (ExecutionException e) {
            throw rethrowable(e.getCause(), onUnexpected);
        } catch (CancellationException e) {
            throw onInterrupt.get();
        }
    }

    public static RuntimeException rethrowable(
            Throwable error, Function<Throwable, ? extends RuntimeException> onUnexpected) {
        Throwable cause = unwrap(error);
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        if (cause instanceof Error fatal) {
            throw fatal;
        }
        return onUnexpected.apply(cause);
    }

    public static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
