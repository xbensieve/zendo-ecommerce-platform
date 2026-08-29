package com.zendo.catalog.domain;

public record ProductDescription(String value) {
    public ProductDescription {
        if (value != null && value.length() > 2000) {
            throw new IllegalArgumentException("Description cannot exceed 2000 characters");
        }
    }
}
