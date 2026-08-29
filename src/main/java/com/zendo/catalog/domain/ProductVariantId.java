package com.zendo.catalog.domain;

import java.util.Objects;
import java.util.UUID;

public record ProductVariantId(UUID value) {
    public ProductVariantId {
        Objects.requireNonNull(value, "Product Variant ID cannot be null");
    }

    public static ProductVariantId generate() {
        return new ProductVariantId(UUID.randomUUID());
    }
    
    public static ProductVariantId fromString(String id) {
        return new ProductVariantId(UUID.fromString(id));
    }
}
