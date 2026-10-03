package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.ErrorCode;
import dev.caerus.sdk.ErrorReason;

import java.util.Optional;
import java.util.OptionalLong;

final class DlsMapping {

    private DlsMapping() {
    }

    static dev.caerus.sdk.internal.proto.dls.LockMode toGrpc(LockMode mode) {
        if (mode == null) {
            return dev.caerus.sdk.internal.proto.dls.LockMode.MODE_UNSPECIFIED;
        }
        switch (mode) {
            case EXCLUSIVE:
                return dev.caerus.sdk.internal.proto.dls.LockMode.EXCLUSIVE;
            case SHARED_READ:
                return dev.caerus.sdk.internal.proto.dls.LockMode.SHARED_READ;
            default:
                return dev.caerus.sdk.internal.proto.dls.LockMode.MODE_UNSPECIFIED;
        }
    }

    static Optional<LockMode> toLockMode(int wire) {
        switch (wire) {
            case dev.caerus.sdk.internal.proto.dls.LockMode.EXCLUSIVE_VALUE:
                return Optional.of(LockMode.EXCLUSIVE);
            case dev.caerus.sdk.internal.proto.dls.LockMode.SHARED_READ_VALUE:
                return Optional.of(LockMode.SHARED_READ);
            default:
                return Optional.empty();
        }
    }

    static LockStatus toLockStatus(int wire) {
        switch (wire) {
            case dev.caerus.sdk.internal.proto.dls.LockStatus.ACQUIRED_VALUE:
                return LockStatus.ACQUIRED;
            case dev.caerus.sdk.internal.proto.dls.LockStatus.DENIED_VALUE:
                return LockStatus.DENIED;
            case dev.caerus.sdk.internal.proto.dls.LockStatus.QUEUED_VALUE:
                return LockStatus.QUEUED;
            default:
                return LockStatus.UNKNOWN;
        }
    }

    static OptionalLong decodeFencingToken(long raw) {
        return raw > 0 ? OptionalLong.of(raw) : OptionalLong.empty();
    }

    static LockHolder assertAcquired(LockHolder holder, String namespace, String lockKey) {
        switch (holder.status()) {
            case ACQUIRED:
                return holder;
            case DENIED:
                throw new LockDeniedError(
                        "Caerus denied the lock on " + namespace + "/" + lockKey
                                + ": it is already held by another transaction.",
                        CaerusErrorOptions.builder().reason(ErrorReason.LOCK_DENIED).build());
            case UNKNOWN:
                throw new DlsError(
                        "Caerus returned a lock status for " + namespace + "/" + lockKey
                                + " that this SDK does not recognise, so whether the lock was granted is unknown.",
                        ErrorCode.UNKNOWN);
            default:
                throw new DlsConflictError("Caerus returned the lock on " + namespace + "/" + lockKey + " as "
                        + holder.status() + ", which this call cannot use.");
        }
    }

    static String text(String value) {
        return value == null ? "" : value;
    }
}
