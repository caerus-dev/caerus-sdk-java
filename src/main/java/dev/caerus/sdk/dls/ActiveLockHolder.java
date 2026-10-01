package dev.caerus.sdk.dls;

import java.util.OptionalLong;

public record ActiveLockHolder(String lockId, long expiresAt, OptionalLong fencingToken) {
}
