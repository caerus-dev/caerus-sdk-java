package dev.caerus.sdk.sre;

import java.util.Optional;

public final class ListResourceHoldersOptions {

    private static final ListResourceHoldersOptions NONE = builder().build();

    private final String resourceKey;
    private final ResourceHolderStatus status;
    private final HolderSort sort;
    private final Integer page;
    private final Integer pageSize;

    private ListResourceHoldersOptions(Builder builder) {
        this.resourceKey = builder.resourceKey;
        this.status = builder.status;
        this.sort = builder.sort;
        this.page = builder.page;
        this.pageSize = builder.pageSize;
    }

    public static ListResourceHoldersOptions none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<String> resourceKey() {
        return Optional.ofNullable(resourceKey);
    }

    public Optional<ResourceHolderStatus> status() {
        return Optional.ofNullable(status);
    }

    public Optional<HolderSort> sort() {
        return Optional.ofNullable(sort);
    }

    public Optional<Integer> page() {
        return Optional.ofNullable(page);
    }

    public Optional<Integer> pageSize() {
        return Optional.ofNullable(pageSize);
    }

    public static final class Builder {

        private String resourceKey;
        private ResourceHolderStatus status;
        private HolderSort sort;
        private Integer page;
        private Integer pageSize;

        private Builder() {
        }

        public Builder resourceKey(String resourceKey) {
            this.resourceKey = resourceKey;
            return this;
        }

        public Builder status(ResourceHolderStatus status) {
            this.status = status;
            return this;
        }

        public Builder sort(HolderSort sort) {
            this.sort = sort;
            return this;
        }

        public Builder page(Integer page) {
            this.page = page;
            return this;
        }

        public Builder pageSize(Integer pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public ListResourceHoldersOptions build() {
            return new ListResourceHoldersOptions(this);
        }
    }
}
