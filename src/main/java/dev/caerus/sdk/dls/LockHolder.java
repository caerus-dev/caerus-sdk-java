package dev.caerus.sdk.dls;

import java.util.OptionalLong;

public record LockHolder(String lockId, OptionalLong fencingToken, LockStatus status) {
}
