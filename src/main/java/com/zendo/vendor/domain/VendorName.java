package com.zendo.vendor.domain;

public record VendorName(String value) {
    public VendorName {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Vendor name cannot be empty");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException("Vendor name must be less than 255 characters");
        }
    }
}
