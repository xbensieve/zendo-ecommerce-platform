package com.zendo.catalog.domain;

public record ProductName(String value) {
    public ProductName {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Product name cannot be empty");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException("Product name must be less than 255 characters");
        }
    }
}
