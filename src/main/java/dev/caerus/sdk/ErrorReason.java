package dev.caerus.sdk;

public final class ErrorReason {

    public static final String OUT_OF_STOCK = "OUT_OF_STOCK";
    public static final String HOLDER_NOT_ACTIVE = "HOLDER_NOT_ACTIVE";
    public static final String RESOURCE_HAS_ACTIVE_HOLDS = "RESOURCE_HAS_ACTIVE_HOLDS";
    public static final String RESOURCE_HAS_QUEUED_REQUESTS = "RESOURCE_HAS_QUEUED_REQUESTS";
    public static final String TEMPLATE_NOT_FOUND = "TEMPLATE_NOT_FOUND";

    public static final String DEADLOCK_DETECTED = "DEADLOCK_DETECTED";
    public static final String TRANSACTION_NOT_ACTIVE = "TRANSACTION_NOT_ACTIVE";
    public static final String LOCK_ALREADY_HELD_EXCLUSIVELY = "LOCK_ALREADY_HELD_EXCLUSIVELY";
    public static final String LOCK_MODE_MISMATCH = "LOCK_MODE_MISMATCH";
    public static final String LOCK_ACQUISITION_CANCELLED = "LOCK_ACQUISITION_CANCELLED";
    public static final String LOCK_DENIED = "LOCK_DENIED";

    public static final String IDEMPOTENCY_KEY_REQUIRED = "IDEMPOTENCY_KEY_REQUIRED";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";

    private ErrorReason() {
    }
}
