package com.zendo.review.api.rest;

import com.zendo.review.api.ReviewQueryApi;
import com.zendo.review.application.ReviewUseCases;
import com.zendo.review.domain.ReviewId;
import com.zendo.shared.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewUseCases reviewUseCases;
    private final ReviewQueryApi reviewQueryApi;

    public ReviewController(ReviewUseCases reviewUseCases, ReviewQueryApi reviewQueryApi) {
        this.reviewUseCases = reviewUseCases;
        this.reviewQueryApi = reviewQueryApi;
    }

    public record CreateReviewRequest(UUID orderItemId, UUID productId, int rating, String content) {}

    @PostMapping("/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> createReview(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody CreateReviewRequest request) {
        try {
            ReviewId reviewId = reviewUseCases.createReview(
                    user.getUserId(),
                    request.orderItemId(),
                    request.productId(),
                    request.rating(),
                    request.content()
            );
            return ResponseEntity.ok().body(reviewId.value());
        } catch (ReviewUseCases.UnauthorizedReviewException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (ReviewUseCases.DuplicateReviewException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    public record UpdateReviewRequest(int rating, String content) {}

    @PutMapping("/reviews/{reviewId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> updateReview(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID reviewId,
            @RequestBody UpdateReviewRequest request) {
        try {
            reviewUseCases.updateReview(user.getUserId(), new ReviewId(reviewId), request.rating(), request.content());
            return ResponseEntity.ok().build();
        } catch (ReviewUseCases.UnauthorizedReviewException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PutMapping("/reviews/{reviewId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> moderateReview(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable UUID reviewId,
            @RequestParam String status) {
        try {
            reviewUseCases.moderateReview(admin.getUserId(), new ReviewId(reviewId), status);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/products/{productId}/reviews/summary")
    public ResponseEntity<?> getProductRatingSummary(@PathVariable UUID productId) {
        return ResponseEntity.ok(reviewQueryApi.getProductRatingSummary(productId));
    }
}
