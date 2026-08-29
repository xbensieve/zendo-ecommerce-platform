package com.zendo.review.domain;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository {
    void save(Review review);
    Optional<Review> findById(ReviewId id);
}
