package com.zendo.promotion.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DiscountRule {
    private final DiscountType type;
    private final BigDecimal value;

    public DiscountRule(DiscountType type, BigDecimal value) {
        if (type == null) {
            throw new PromotionException("DiscountType cannot be null");
        }
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new PromotionException("Discount value must be non-negative");
        }
        if (type == DiscountType.PERCENTAGE && value.compareTo(new BigDecimal("100")) > 0) {
            throw new PromotionException("Percentage discount cannot exceed 100");
        }
        this.type = type;
        this.value = value;
    }

    public BigDecimal applyDiscount(BigDecimal originalPrice) {
        if (originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new PromotionException("Original price must be non-negative");
        }

        BigDecimal discountAmount = calculateDiscountAmount(originalPrice);
        BigDecimal finalPrice = originalPrice.subtract(discountAmount);

        // Ensure final price doesn't go below zero
        if (finalPrice.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return finalPrice;
    }

    public BigDecimal calculateDiscountAmount(BigDecimal originalPrice) {
        if (type == DiscountType.FIXED_AMOUNT) {
            // Cap discount at the original price
            return value.min(originalPrice);
        } else {
            // PERCENTAGE
            return originalPrice.multiply(value).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }
    }

    public DiscountType getType() {
        return type;
    }

    public BigDecimal getValue() {
        return value;
    }
}
