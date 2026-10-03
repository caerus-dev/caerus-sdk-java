package dev.caerus.sdk.sre;

import java.util.Optional;

public final class GetResourcesByGroupOptions {

    private static final GetResourcesByGroupOptions NONE = builder().build();

    private final Integer page;
    private final Integer pageSize;

    private GetResourcesByGroupOptions(Builder builder) {
        this.page = builder.page;
        this.pageSize = builder.pageSize;
    }

    public static GetResourcesByGroupOptions none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Integer> page() {
        return Optional.ofNullable(page);
    }

    public Optional<Integer> pageSize() {
        return Optional.ofNullable(pageSize);
    }

    public static final class Builder {

        private Integer page;
        private Integer pageSize;

        private Builder() {
        }

        public Builder page(Integer page) {
            this.page = page;
            return this;
        }

        public Builder pageSize(Integer pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public GetResourcesByGroupOptions build() {
            return new GetResourcesByGroupOptions(this);
        }
    }
}
