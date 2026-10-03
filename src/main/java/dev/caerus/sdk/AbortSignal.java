package dev.caerus.sdk;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AbortSignal {

    private final Object lock = new Object();
    private final List<Runnable> listeners = new ArrayList<>();
    private volatile boolean aborted;
    private volatile Throwable reason;

    AbortSignal() {
    }

    public boolean aborted() {
        return aborted;
    }

    public Optional<Throwable> reason() {
        return Optional.ofNullable(reason);
    }

    public Runnable onAbort(Runnable listener) {
        synchronized (lock) {
            if (!aborted) {
                listeners.add(listener);
                return () -> {
                    synchronized (lock) {
                        listeners.remove(listener);
                    }
                };
            }
        }
        listener.run();
        return () -> {
        };
    }

    void abort(Throwable cause) {
        List<Runnable> toRun;
        synchronized (lock) {
            if (aborted) {
                return;
            }
            reason = cause;
            aborted = true;
            toRun = new ArrayList<>(listeners);
            listeners.clear();
        }
        RuntimeException first = null;
        for (Runnable listener : toRun) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
