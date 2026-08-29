package com.zendo.review.application;

import com.zendo.order.api.OrderQueryApi;
import com.zendo.review.domain.Rating;
import com.zendo.review.domain.Review;
import com.zendo.review.domain.ReviewId;
import com.zendo.review.domain.ReviewRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ReviewUseCases {

    private final ReviewRepository reviewRepository;
    private final OrderQueryApi orderQueryApi;

    public ReviewUseCases(ReviewRepository reviewRepository, OrderQueryApi orderQueryApi) {
        this.reviewRepository = reviewRepository;
        this.orderQueryApi = orderQueryApi;
    }

    @Transactional
    public ReviewId createReview(String customerId, UUID orderItemId, UUID productId, int ratingValue, String content) {
        // 1. Check purchase eligibility
        if (!orderQueryApi.isEligibleForReview(customerId, orderItemId)) {
            throw new UnauthorizedReviewException("Customer is not eligible to review this item. Item may not belong to the customer or order is not paid.");
        }

        // 2. Create domain model
        Rating rating = new Rating(ratingValue);
        Review review = new Review(ReviewId.generate(), customerId, orderItemId, productId, rating, content);

        try {
            // 3. Persist
            reviewRepository.save(review);
            return review.getId();
        } catch (DataIntegrityViolationException e) {
            // Unique constraint on order_item_id
            throw new DuplicateReviewException("A review already exists for this order item");
        }
    }

    @Transactional
    public void updateReview(String customerId, ReviewId reviewId, int ratingValue, String content) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewException("Review not found"));

        if (!review.getCustomerId().equals(customerId)) {
            throw new UnauthorizedReviewException("You can only edit your own reviews");
        }

        review.updateContentAndRating(content, new Rating(ratingValue));
        reviewRepository.save(review);
    }

    @Transactional
    public void moderateReview(String adminId, ReviewId reviewId, String newStatus) {
        // Admin authorization should be handled in the controller (e.g. @PreAuthorize("hasRole('ADMIN')"))
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewException("Review not found"));

        switch (newStatus.toUpperCase()) {
            case "PUBLISHED":
                review.publish();
                break;
            case "HIDDEN":
                review.hide();
                break;
            case "REJECTED":
                review.reject();
                break;
            default:
                throw new ReviewException("Invalid review status: " + newStatus);
        }

        reviewRepository.save(review);
    }

    public static class UnauthorizedReviewException extends RuntimeException {
        public UnauthorizedReviewException(String message) {
            super(message);
        }
    }

    public static class DuplicateReviewException extends RuntimeException {
        public DuplicateReviewException(String message) {
            super(message);
        }
    }
}
