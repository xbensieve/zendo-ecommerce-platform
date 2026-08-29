package com.zendo.review.domain;

import java.util.UUID;

public record ReviewId(UUID value) {
    public ReviewId {
        if (value == null) {
            throw new IllegalArgumentException("ReviewId cannot be null");
        }
    }
    
    public static ReviewId generate() {
        return new ReviewId(UUID.randomUUID());
    }
}
