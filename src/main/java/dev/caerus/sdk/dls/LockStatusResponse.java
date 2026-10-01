package dev.caerus.sdk.dls;

import java.util.List;
import java.util.Optional;

public record LockStatusResponse(
        boolean isHeld,
        Optional<LockMode> currentMode,
        List<ActiveLockHolder> activeHolders,
        int pendingQueueSize) {

    public LockStatusResponse {
        activeHolders = List.copyOf(activeHolders);
    }
}
