package dev.caerus.sdk.sre;

final class Validation {

    private Validation() {
    }

    static void requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationError(name + " is required");
        }
    }

    static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new ValidationError(name + " must be greater than zero");
        }
    }

    static void requireKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new ValidationError("a resource key is required");
        }
    }
}
