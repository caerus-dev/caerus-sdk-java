package dev.caerus.sdk;

@FunctionalInterface
public interface CaerusLogger {

    void error(String message, Object... details);
}
