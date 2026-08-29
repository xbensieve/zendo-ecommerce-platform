package com.zendo.catalog.domain;

import java.util.UUID;

public record VendorId(UUID value) {
    public VendorId {
        if (value == null) {
            throw new CatalogException("Vendor ID cannot be null");
        }
    }

    public static VendorId fromString(String id) {
        try {
            return new VendorId(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new CatalogException("Invalid Vendor ID format");
        }
    }

    public static VendorId generate() {
        return new VendorId(UUID.randomUUID());
    }
}
