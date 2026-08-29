package com.zendo.vendor.domain;

import java.util.Objects;
import java.util.UUID;

public record VendorId(UUID value) {
    public VendorId {
        Objects.requireNonNull(value, "Vendor ID cannot be null");
    }

    public static VendorId generate() {
        return new VendorId(UUID.randomUUID());
    }

    public static VendorId fromString(String id) {
        return new VendorId(UUID.fromString(id));
    }
}
