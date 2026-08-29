package com.zendo.catalog.domain;

import java.util.Objects;
import java.util.UUID;

public record ProductId(UUID value) {
    public ProductId {
        Objects.requireNonNull(value, "Product ID cannot be null");
    }

    public static ProductId generate() {
        return new ProductId(UUID.randomUUID());
    }

    public static ProductId fromString(String id) {
        return new ProductId(UUID.fromString(id));
    }
}
