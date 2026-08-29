package com.zendo.catalog.domain;

public record SKU(String value) {
    public SKU(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("SKU cannot be empty");
        }
        if (!value.matches("^[a-zA-Z0-9-_]+$")) {
            throw new IllegalArgumentException("SKU contains invalid characters");
        }
        this.value = value.trim().toUpperCase();
    }
}
