package com.zendo.identity.domain;

import java.util.Objects;

/**
 * Value object representing a User's unique identifier.
 */
public record UserId(String value) {
    public UserId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UserId value cannot be null or blank");
        }
    }
}
