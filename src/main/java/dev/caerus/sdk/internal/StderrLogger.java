package dev.caerus.sdk.internal;

import dev.caerus.sdk.CaerusLogger;

import java.io.PrintStream;

public final class StderrLogger implements CaerusLogger {

    private final String prefix;

    public StderrLogger(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void error(String message, Object... details) {
        PrintStream err = System.err;
        StringBuilder line = new StringBuilder(prefix).append(' ').append(message);
        Throwable throwable = null;
        if (details != null) {
            for (Object detail : details) {
                if (detail instanceof Throwable t && throwable == null) {
                    throwable = t;
                } else {
                    line.append(' ').append(detail);
                }
            }
        }
        synchronized (err) {
            err.println(line);
            if (throwable != null) {
                throwable.printStackTrace(err);
            }
        }
    }

    public String prefix() {
        return prefix;
    }
}
