package dev.caerus.sdk.sre;

import java.util.List;

public record ResourceHolderPage(List<ResourceHolder> holders, boolean hasNextPage) {

    public ResourceHolderPage {
        holders = List.copyOf(holders);
    }
}
