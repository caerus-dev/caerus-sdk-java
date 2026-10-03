package dev.caerus.sdk.sre;

import java.util.List;

public record ResourcePage(List<Resource> resources, boolean hasNextPage) {

    public ResourcePage {
        resources = List.copyOf(resources);
    }
}
