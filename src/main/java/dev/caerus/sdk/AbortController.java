package dev.caerus.sdk;

public final class AbortController {

    private final AbortSignal signal = new AbortSignal();

    public AbortSignal signal() {
        return signal;
    }

    public void abort() {
        signal.abort(null);
    }

    public void abort(Throwable reason) {
        signal.abort(reason);
    }
}
