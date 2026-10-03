package dev.caerus.sdk.sre;

import java.util.List;

public record MockSnapshot(List<Resource> resources, List<ResourceHolder> holders) {

    public MockSnapshot {
        resources = List.copyOf(resources);
        holders = List.copyOf(holders);
    }
}
