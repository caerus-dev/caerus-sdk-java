package dev.caerus.sdk.dls;

public record TransactionLockInfo(String namespace, String lockKey, LockMode requestedMode, LockStatus status) {
}
