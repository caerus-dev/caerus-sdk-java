package dev.caerus.sdk.dls;

import java.util.List;
import java.util.Optional;

public record TransactionStatusResponse(
        String status,
        Optional<String> abortReason,
        List<TransactionLockInfo> locks,
        long expiresAt) {

    public TransactionStatusResponse {
        locks = List.copyOf(locks);
    }
}
