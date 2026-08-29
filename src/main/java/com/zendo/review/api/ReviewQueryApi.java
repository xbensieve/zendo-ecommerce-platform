package com.zendo.review.api;

import java.util.UUID;

public interface ReviewQueryApi {

    record RatingSummary(double averageRating, long totalReviews) {}

    RatingSummary getProductRatingSummary(UUID productId);
}
