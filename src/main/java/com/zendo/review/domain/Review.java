package com.zendo.review.domain;

import java.time.Instant;
import java.util.UUID;

public class Review {
    private final ReviewId id;
    private final String customerId;
    private final UUID orderItemId;
    private final UUID productId;
    private Rating rating;
    private String content;
    private ReviewStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    public Review(ReviewId id, String customerId, UUID orderItemId, UUID productId, Rating rating, String content) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID cannot be empty");
        }
        if (orderItemId == null) {
            throw new IllegalArgumentException("Order Item ID cannot be null");
        }
        if (productId == null) {
            throw new IllegalArgumentException("Product ID cannot be null");
        }
        validateContent(content);

        this.id = id;
        this.customerId = customerId;
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.rating = rating;
        this.content = content;
        this.status = ReviewStatus.PUBLISHED; // Default to PUBLISHED as per plan
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    // For reconstitution
    public Review(ReviewId id, String customerId, UUID orderItemId, UUID productId, Rating rating, String content, ReviewStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.rating = rating;
        this.content = content;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    private void validateContent(String content) {
        if (content != null && content.length() > 1000) {
            throw new IllegalArgumentException("Review content must be less than 1000 characters");
        }
    }

    public void updateContentAndRating(String newContent, Rating newRating) {
        validateContent(newContent);
        this.content = newContent;
        this.rating = newRating;
        this.updatedAt = Instant.now();
        // Depending on policy, we might transition to PENDING. Plan says keep it as is.
    }

    public void publish() {
        this.status = ReviewStatus.PUBLISHED;
        this.updatedAt = Instant.now();
    }

    public void hide() {
        this.status = ReviewStatus.HIDDEN;
        this.updatedAt = Instant.now();
    }

    public void reject() {
        this.status = ReviewStatus.REJECTED;
        this.updatedAt = Instant.now();
    }

    public ReviewId getId() { return id; }
    public String getCustomerId() { return customerId; }
    public UUID getOrderItemId() { return orderItemId; }
    public UUID getProductId() { return productId; }
    public Rating getRating() { return rating; }
    public String getContent() { return content; }
    public ReviewStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
