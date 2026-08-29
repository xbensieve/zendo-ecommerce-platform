package com.zendo.promotion.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PromotionQueryApi {
    
    record PromotionDetails(
        String promotionRef,
        String discountType,
        BigDecimal discountValue
    ) {}

    /**
     * Retrieves all applicable promotions for a given product/vendor.
     * Evaluates both campaigns and an optional coupon code.
     */
    List<PromotionDetails> getApplicablePromotions(String vendorId, String productId, String couponCode);
}
