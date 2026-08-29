package com.zendo.review.infrastructure.persistence;

import com.zendo.review.domain.Rating;
import com.zendo.review.domain.Review;
import com.zendo.review.domain.ReviewId;
import com.zendo.review.domain.ReviewRepository;
import com.zendo.review.domain.ReviewStatus;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ReviewRepositoryImpl implements ReviewRepository {

    private final SpringDataReviewRepository jpaRepository;

    public ReviewRepositoryImpl(SpringDataReviewRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Review review) {
        ReviewEntity entity = new ReviewEntity();
        entity.setId(review.getId().value());
        entity.setCustomerId(review.getCustomerId());
        entity.setOrderItemId(review.getOrderItemId());
        entity.setProductId(review.getProductId());
        entity.setRating(review.getRating().value());
        entity.setContent(review.getContent());
        entity.setStatus(review.getStatus().name());
        entity.setCreatedAt(review.getCreatedAt());
        entity.setUpdatedAt(review.getUpdatedAt());

        // Use saveAndFlush to catch constraints within the try-catch block of use cases
        jpaRepository.saveAndFlush(entity);
    }

    @Override
    public Optional<Review> findById(ReviewId id) {
        return jpaRepository.findById(id.value()).map(this::toDomain);
    }

    private Review toDomain(ReviewEntity entity) {
        return new Review(
                new ReviewId(entity.getId()),
                entity.getCustomerId(),
                entity.getOrderItemId(),
                entity.getProductId(),
                new Rating(entity.getRating()),
                entity.getContent(),
                ReviewStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
