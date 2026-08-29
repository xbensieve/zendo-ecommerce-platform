package com.zendo.review.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SpringDataReviewRepository extends JpaRepository<ReviewEntity, UUID> {
}
