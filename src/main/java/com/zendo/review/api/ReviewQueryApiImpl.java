package com.zendo.review.api;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ReviewQueryApiImpl implements ReviewQueryApi {

    private final JdbcClient jdbcClient;

    public ReviewQueryApiImpl(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public RatingSummary getProductRatingSummary(UUID productId) {
        String sql = """
            SELECT AVG(rating) as avg_rating, COUNT(id) as total_reviews
            FROM review_ctx.reviews
            WHERE product_id = :productId AND status = 'PUBLISHED'
        """;
        
        return jdbcClient.sql(sql)
                .param("productId", productId)
                .query(rs -> {
                    if (rs.next()) {
                        double avg = rs.getDouble("avg_rating");
                        long count = rs.getLong("total_reviews");
                        return new RatingSummary(avg, count);
                    }
                    return new RatingSummary(0.0, 0);
                });
    }
}
